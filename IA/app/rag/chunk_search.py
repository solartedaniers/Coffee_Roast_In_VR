from typing import Protocol

from app.rag.database import Database
from app.rag.embeddings import OllamaEmbeddings
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
