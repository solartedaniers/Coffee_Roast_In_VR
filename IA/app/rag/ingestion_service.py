"""Lee los PDFs de rag-docs/, los parte y guarda sus embeddings en pgvector.

Solo corre cuando el backend lo pide (POST /api/v1/admin/rag/ingest, que valida al ADMIN). Un
archivo cuyo hash ya está registrado se salta, así que agregar un PDF no reindexa los demás.
"""
import hashlib
import logging
from pathlib import Path

from app.contracts import FileResult, IngestionStatus, RagIngestionSummaryResponse
from app.rag.database import Database
from app.rag.document import Document
from app.rag.document_embedder import DocumentEmbedder
from app.rag.embeddings import OllamaEmbeddings
from app.rag.ingested_document_repository import IngestedDocument, IngestedDocumentRepository
from app.rag.pdf_reader import FILE_NAME_KEY, PdfPageReader
from app.rag.text_splitter import TextSplitter
from app.rag.vector_store import PgVectorStore

log = logging.getLogger(__name__)
CONTENT_HASH_KEY = "content_hash"
PDF_PATTERN = "*.pdf"


class RagIngestionService:
    def __init__(self, database: Database, vector_table: str, docs_dir: Path, reader: PdfPageReader,
                 splitter: TextSplitter, embeddings: OllamaEmbeddings, embedding_batch_size: int,
                 document_prefix: str = ""):
        self.database = database
        self.vector_table = vector_table
        self.docs_dir = docs_dir
        self.reader = reader
        self.splitter = splitter
        self.embedder = DocumentEmbedder(embeddings, embedding_batch_size, document_prefix)

    def ingest_all(self) -> RagIngestionSummaryResponse:
        paths = sorted(self.docs_dir.glob(PDF_PATTERN)) if self.docs_dir.is_dir() else []
        if not paths:
            log.warning("No PDFs found under %s", self.docs_dir)
        return RagIngestionSummaryResponse.from_files([self.ingest_one(path) for path in paths])

    def ingest_one(self, path: Path) -> FileResult:
        try:
            content_hash = hashlib.sha256(path.read_bytes()).hexdigest()
        except OSError as error:
            log.warning("Could not read %s: %s", path.name, error)
            return FileResult(file_name=path.name, status=IngestionStatus.READ_ERROR, chunk_count=0)

        with self.database.transaction() as connection:
            existing = IngestedDocumentRepository(connection).find_by_file_name(path.name)
        if existing and existing.content_hash == content_hash:
            return FileResult(file_name=path.name, status=IngestionStatus.SKIPPED_UNCHANGED,
                              chunk_count=existing.chunk_count)

        try:
            return self._split_and_store(path, content_hash, existing)
        except Exception as error:
            # Un PDF dañado no debe tumbar el lote: se marca FAILED y se sigue con los demás.
            log.warning("Could not embed %s: %s", path.name, error)
            return FileResult(file_name=path.name, status=IngestionStatus.FAILED, chunk_count=0,
                              error_message=str(error))

    def _split_and_store(self, path: Path, content_hash: str, existing: IngestedDocument | None) -> FileResult:
        pages = [Document(page.text, page.metadata | {FILE_NAME_KEY: path.name, CONTENT_HASH_KEY: content_hash})
                 for page in self.reader.read(path)]
        chunks = self.splitter.split_documents(pages)
        vectors = self.embedder.embed([chunk.text for chunk in chunks])

        # Todo en una transacción: si algo falla no se pierden los fragmentos anteriores ni queda
        # registrado un archivo sin fragmentos (que se saltaría para siempre).
        with self.database.transaction() as connection:
            store = PgVectorStore(connection, self.vector_table)
            repository = IngestedDocumentRepository(connection)
            if existing:
                store.delete_by_file_name(path.name)
            store.add(chunks, vectors)
            if existing:
                repository.mark_reingested(existing.id, content_hash, len(chunks))
            else:
                repository.save_new(path.name, content_hash, len(chunks))

        status = IngestionStatus.REINGESTED if existing else IngestionStatus.INGESTED
        return FileResult(file_name=path.name, status=status, chunk_count=len(chunks))
