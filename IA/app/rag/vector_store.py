import json
import uuid
from dataclasses import dataclass

from psycopg import Connection, sql

from app.rag.document import Document

FILE_NAME_METADATA_KEY = "file_name"
# Spring AI usaba 1 - similarityThreshold con umbral 0: solo descarta similitud coseno <= 0.
MAX_COSINE_DISTANCE = 1.0


@dataclass(frozen=True)
class ScoredChunk:
    text: str
    metadata: dict[str, object]
    score: float


def vector_literal(vector: list[float]) -> str:
    return "[" + ",".join(repr(float(number)) for number in vector) + "]"


class PgVectorStore:
    """Fragmentos y embeddings en la tabla que antes manejaba Spring AI (mismas columnas)."""

    def __init__(self, connection: Connection, table: str):
        self.connection = connection
        self.table = sql.Identifier(table)

    def add(self, documents: list[Document], vectors: list[list[float]]) -> None:
        if len(documents) != len(vectors):
            raise ValueError("Each document needs one embedding")
        statement = sql.SQL("INSERT INTO {} (id, content, metadata, embedding) "
                            "VALUES (%s, %s, %s::json, %s::vector)").format(self.table)
        with self.connection.cursor() as cursor:
            cursor.executemany(statement, [
                (uuid.uuid4(), document.text, json.dumps(document.metadata, ensure_ascii=False), vector_literal(vector))
                for document, vector in zip(documents, vectors, strict=True)
            ])

    def delete_all(self) -> None:
        self.connection.execute(sql.SQL("DELETE FROM {}").format(self.table))

    def delete_by_file_name(self, file_name: str) -> None:
        self.connection.execute(
            sql.SQL("DELETE FROM {} WHERE metadata->>%s = %s").format(self.table),
            (FILE_NAME_METADATA_KEY, file_name),
        )

    def similarity_search(self, vector: list[float], top_k: int) -> list[ScoredChunk]:
        literal = vector_literal(vector)
        rows = self.connection.execute(
            sql.SQL("SELECT content, metadata, embedding <=> %s::vector AS distance FROM {} "
                    "WHERE embedding <=> %s::vector < %s ORDER BY distance LIMIT %s").format(self.table),
            (literal, literal, MAX_COSINE_DISTANCE, top_k),
        ).fetchall()
        return [ScoredChunk(content, metadata or {}, 1.0 - distance) for content, metadata, distance in rows]
