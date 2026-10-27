import json
import re
import uuid
from dataclasses import dataclass

from psycopg import Connection, sql

from app.rag.document import Document

FILE_NAME_METADATA_KEY = "file_name"
# Spring AI usaba 1 - similarityThreshold con umbral 0: solo descarta similitud coseno <= 0.
MAX_COSINE_DISTANCE = 1.0
WORD_PATTERN = re.compile(r"[^\W_]+")


@dataclass(frozen=True)
class ScoredChunk:
    text: str
    metadata: dict[str, object]
    # Similitud coseno con la búsqueda, también en los fragmentos encontrados por texto.
    score: float
    chunk_id: str = ""


def any_word_query(text: str) -> str:
    """Consulta para websearch_to_tsquery que acepta cualquiera de las palabras.

    Exigir todas dejaba sin resultados casi cualquier pregunta larga. Las palabras vacías las
    descarta la configuración de texto de Postgres.
    """
    return " or ".join(WORD_PATTERN.findall(text))


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

    def delete_by_file_name(self, file_name: str) -> None:
        self.connection.execute(
            sql.SQL("DELETE FROM {} WHERE metadata->>%s = %s").format(self.table),
            (FILE_NAME_METADATA_KEY, file_name),
        )

    def similarity_search(self, vector: list[float], top_k: int) -> list[ScoredChunk]:
        literal = vector_literal(vector)
        rows = self.connection.execute(
            sql.SQL("SELECT id, content, metadata, embedding <=> %s::vector AS distance FROM {} "
                    "WHERE embedding <=> %s::vector < %s ORDER BY distance LIMIT %s").format(self.table),
            (literal, literal, MAX_COSINE_DISTANCE, top_k),
        ).fetchall()
        return [self._scored(row) for row in rows]

    def lexical_search(self, query_text: str, vector: list[float], text_search_config: str,
                       limit: int, max_document_ratio: float = 1.0) -> list[ScoredChunk]:
        """Búsqueda de texto completo de Postgres, ordenada por ts_rank_cd.

        ts_rank_cd no pondera qué tan rara es cada palabra, así que las que aparecen en más de
        `max_document_ratio` de los fragmentos (como "café" o "tueste") se quitan de la consulta:
        si no, llenan los resultados de fragmentos que solo las repiten. Con 1.0 no se quita ninguna.

        El tsvector se calcula en cada consulta: con unos miles de fragmentos no hace falta
        una columna ni un índice nuevos, y el esquema queda igual.
        """
        words = any_word_query(query_text)
        if not words:
            return []
        corpus = sql.SQL("SELECT to_tsvector({}::regconfig, content) FROM {}").format(
            sql.Literal(text_search_config), self.table).as_string(self.connection)
        rows = self.connection.execute(
            sql.SQL("WITH total AS (SELECT count(*) AS chunks FROM {table}), "
                    "kept AS (SELECT term.lexeme FROM unnest(to_tsvector(%(config)s::regconfig, %(words)s)) AS term "
                    "JOIN ts_stat(%(corpus)s) AS stat ON stat.word = term.lexeme, total "
                    "WHERE stat.ndoc <= %(ratio)s * total.chunks), "
                    "query AS (SELECT string_agg(quote_literal(lexeme), ' | ')::tsquery AS terms FROM kept) "
                    "SELECT id, content, metadata, embedding <=> %(vector)s::vector AS distance FROM {table}, query "
                    "WHERE query.terms IS NOT NULL AND to_tsvector(%(config)s::regconfig, content) @@ query.terms "
                    "ORDER BY ts_rank_cd(to_tsvector(%(config)s::regconfig, content), query.terms) DESC, id "
                    "LIMIT %(limit)s").format(table=self.table),
            {"vector": vector_literal(vector), "config": text_search_config, "words": words,
             "corpus": corpus, "ratio": max_document_ratio, "limit": limit},
        ).fetchall()
        return [self._scored(row) for row in rows]

    @staticmethod
    def _scored(row) -> ScoredChunk:
        chunk_id, content, metadata, distance = row
        return ScoredChunk(content, metadata or {}, 1.0 - distance, str(chunk_id))
