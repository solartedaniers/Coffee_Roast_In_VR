from typing import Protocol

from app.rag.database import Database
from app.rag.embeddings import OllamaEmbeddings
from app.rag.pdf_reader import FILE_NAME_KEY, PAGE_NUMBER_KEY
from app.rag.query_translator import QueryTranslator
from app.rag.vector_store import PgVectorStore, ScoredChunk


class ChunkSearch(Protocol):
    """Fragmentos más relevantes para un texto de búsqueda, del más al menos relevante."""

    def search(self, query: str, top_k: int) -> list[ScoredChunk]: ...


class SemanticChunkSearch:
    """Similitud coseno entre el embedding de la búsqueda y los fragmentos indexados.

    `query_prefix` es la instrucción de tarea que esperan algunos modelos de embeddings
    (nomic-embed-text usa "search_query: "); se agrega solo al calcular el embedding.
    """

    def __init__(self, database: Database, vector_table: str, embeddings: OllamaEmbeddings, query_prefix: str = ""):
        self.database = database
        self.vector_table = vector_table
        self.embeddings = embeddings
        self.query_prefix = query_prefix

    def search(self, query: str, top_k: int) -> list[ScoredChunk]:
        vector = self.embeddings.embed_one(self.query_prefix + query)
        with self.database.transaction() as connection:
            return PgVectorStore(connection, self.vector_table).similarity_search(vector, top_k)


class TranslatedChunkSearch:
    """Busca con el texto original y con su traducción, y une ambos resultados.

    Las dos listas vienen del mismo modelo de embeddings, así que su similitud coseno se compara
    directamente: de cada fragmento se queda la mayor.
    """

    def __init__(self, inner: ChunkSearch, translator: QueryTranslator):
        self.inner = inner
        self.translator = translator

    def search(self, query: str, top_k: int) -> list[ScoredChunk]:
        found = self.inner.search(query, top_k)
        translated = self.translator.translate(query)
        if translated and translated != query:
            found = found + self.inner.search(translated, top_k)
        best: dict[tuple[object, object, str], ScoredChunk] = {}
        for chunk in found:
            key = (chunk.metadata.get(FILE_NAME_KEY), chunk.metadata.get(PAGE_NUMBER_KEY), chunk.text)
            if key not in best or chunk.score > best[key].score:
                best[key] = chunk
        return sorted(best.values(), key=lambda chunk: chunk.score, reverse=True)[:top_k]
