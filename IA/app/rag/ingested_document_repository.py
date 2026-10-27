from dataclasses import dataclass
from datetime import UTC, datetime

from psycopg import Connection, sql

# Mismo nombre de tabla que la entidad JPA del servicio en Java.
INGESTED_DOCUMENTS_TABLE = "rag_ingested_documents"


@dataclass(frozen=True)
class IngestedDocument:
    id: int
    file_name: str
    content_hash: str
    chunk_count: int


class IngestedDocumentRepository:
    """Qué PDFs ya están en pgvector, por nombre y hash, para no volver a procesarlos."""

    def __init__(self, connection: Connection, now=lambda: datetime.now(UTC)):
        self.connection = connection
        self.now = now
        self.table = sql.Identifier(INGESTED_DOCUMENTS_TABLE)

    def find_by_file_name(self, file_name: str) -> IngestedDocument | None:
        row = self.connection.execute(
            sql.SQL("SELECT id, file_name, content_hash, chunk_count FROM {} WHERE file_name = %s").format(self.table),
            (file_name,),
        ).fetchone()
        return IngestedDocument(*row) if row else None

    def save_new(self, file_name: str, content_hash: str, chunk_count: int) -> None:
        self.connection.execute(
            sql.SQL("INSERT INTO {} (file_name, content_hash, chunk_count, ingested_at) "
                    "VALUES (%s, %s, %s, %s)").format(self.table),
            (file_name, content_hash, chunk_count, self.now()),
        )

    def mark_reingested(self, document_id: int, content_hash: str, chunk_count: int) -> None:
        self.connection.execute(
            sql.SQL("UPDATE {} SET content_hash = %s, chunk_count = %s, ingested_at = %s WHERE id = %s").format(
                self.table),
            (content_hash, chunk_count, self.now(), document_id),
        )
