"""Cuántos fragmentos hay por archivo en pgvector y en qué estado están. Solo consultas."""
from dataclasses import dataclass

from psycopg import Connection, sql

from app.rag.pdf_reader import FILE_NAME_KEY, PAGE_NUMBER_KEY


@dataclass(frozen=True)
class FileChunkStats:
    file_name: str
    chunks: int
    average_chars: int
    short_chunks: int
    chunks_without_page: int
    pages_with_chunks: int


class IndexStatistics:
    def __init__(self, connection: Connection, vector_table: str):
        self.connection = connection
        self.table = sql.Identifier(vector_table)

    def per_file(self, short_chunk_chars: int) -> list[FileChunkStats]:
        rows = self.connection.execute(
            sql.SQL("SELECT metadata->>%s, count(*), round(avg(length(content)))::int, "
                    "count(*) FILTER (WHERE length(content) < %s), "
                    "count(*) FILTER (WHERE metadata->>%s IS NULL), "
                    "count(DISTINCT metadata->>%s) FROM {} GROUP BY 1 ORDER BY 1").format(self.table),
            (FILE_NAME_KEY, short_chunk_chars, PAGE_NUMBER_KEY, PAGE_NUMBER_KEY),
        ).fetchall()
        return [FileChunkStats(*row) for row in rows]

    def duplicated_chunks(self) -> int:
        """Fragmentos cuyo texto ya está en otro fragmento."""
        return self.connection.execute(
            sql.SQL("SELECT count(*) - count(DISTINCT md5(content)) FROM {}").format(self.table)).fetchone()[0]
