from app.contracts import IngestionStatus
from app.rag.ingested_document_repository import IngestedDocumentRepository
from app.rag.ingestion_service import RagIngestionService
from app.rag.pdf_reader import PdfPageReader
from app.rag.text_splitter import CharacterTextSplitter
from app.rag.vector_store import PgVectorStore
from tests.support.pdf_factory import write_text_pdf


class FixedEmbeddings:
    def __init__(self, dimensions):
        self.dimensions = dimensions

    def embed(self, texts):
        return [[1.0] + [0.0] * (self.dimensions - 1) for _ in texts]


def service(database, schema, docs_dir, dimensions) -> RagIngestionService:
    return RagIngestionService(database, schema.vector_table, docs_dir, PdfPageReader(),
                               CharacterTextSplitter(700, 100), FixedEmbeddings(dimensions), 32)


def stored_texts(database, schema):
    with database.transaction() as connection:
        return [chunk.text for chunk in PgVectorStore(connection, schema.vector_table).similarity_search([1, 0, 0], 10)]


def test_a_failed_reingestion_keeps_the_previous_chunks(tmp_path, database, schema):
    write_text_pdf(tmp_path / "it-manual.pdf", ["Version uno"])
    assert service(database, schema, tmp_path, 3).ingest_all().files[0].status is IngestionStatus.INGESTED

    write_text_pdf(tmp_path / "it-manual.pdf", ["Version dos"])
    # Vectores de otra dimensión: el insert falla después de borrar los fragmentos viejos.
    result = service(database, schema, tmp_path, 4).ingest_all().files[0]

    assert result.status is IngestionStatus.FAILED
    assert stored_texts(database, schema) == ["Version uno"]
    with database.transaction() as connection:
        assert IngestedDocumentRepository(connection).find_by_file_name("it-manual.pdf").chunk_count == 1
