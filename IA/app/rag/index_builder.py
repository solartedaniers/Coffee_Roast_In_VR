"""Construye desde cero un índice de fragmentos en una tabla aparte, para medir variantes.

No usa el registro de ingesta (rag_ingested_documents): la tabla se vacía y se llena completa,
así que sirve para comparar otra partición del texto, otro prefijo u otro modelo de embeddings.
"""
from dataclasses import dataclass
from pathlib import Path

from app.rag.database import Database
from app.rag.document import Document
from app.rag.document_embedder import DocumentEmbedder
from app.rag.pdf_reader import PdfPageReader
from app.rag.schema import RagSchema
from app.rag.text_splitter import TextSplitter
from app.rag.vector_store import PgVectorStore


@dataclass(frozen=True)
class BuiltIndex:
    table: str
    chunks_per_file: dict[str, int]


class RagIndexBuilder:
    def __init__(self, database: Database, reader: PdfPageReader, splitter: TextSplitter,
                 embedder: DocumentEmbedder, dimensions: int):
        self.database = database
        self.reader = reader
        self.splitter = splitter
        self.embedder = embedder
        self.dimensions = dimensions

    def build(self, table: str, pdf_paths: list[Path]) -> BuiltIndex:
        chunks: list[Document] = []
        chunks_per_file = {}
        for path in pdf_paths:
            file_chunks = self.splitter.split_documents(self.reader.read(path))
            chunks_per_file[path.name] = len(file_chunks)
            chunks.extend(file_chunks)
        # Los embeddings se calculan antes de abrir la transacción, que así dura poco.
        vectors = self.embedder.embed([chunk.text for chunk in chunks])
        RagSchema(self.database, table, self.dimensions).ensure()
        with self.database.transaction() as connection:
            store = PgVectorStore(connection, table)
            store.delete_all()
            store.add(chunks, vectors)
        return BuiltIndex(table, chunks_per_file)
