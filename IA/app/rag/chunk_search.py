from typing import Protocol

from app.rag.database import Database
from app.rag.embeddings import OllamaEmbeddings
from app.rag.rank_fusion import ReciprocalRankFusion
from app.rag.vector_store import PgVectorStore, ScoredChunk


class ChunkSearch(Protocol):
    """Fragmentos más relevantes para un texto de búsqueda, del más al menos relevante."""

    def search(self, query: str, top_k: int) -> list[ScoredChunk]: ...


class SemanticChunkSearch:
    """Similitud coseno entre el embedding de la búsqueda y los fragmentos indexados."""

    def __init__(self, database: Database, vector_table: str, embeddings: OllamaEmbeddings):
        self.database = database
        self.vector_table = vector_table
        self.embeddings = embeddings

    def search(self, query: str, top_k: int) -> list[ScoredChunk]:
        vector = self.embeddings.embed_one(query)
        with self.database.transaction() as connection:
            return PgVectorStore(connection, self.vector_table).similarity_search(vector, top_k)


class HybridChunkSearch:
    """Búsqueda semántica y de texto completo en español, combinadas con RRF.

    Recupera los fragmentos que comparten palabras exactas con la pregunta (por ejemplo "horneado"
    o "Agtron") aunque su embedding no quede entre los más cercanos.
    """

    def __init__(self, database: Database, vector_table: str, embeddings: OllamaEmbeddings,
                 fusion: ReciprocalRankFusion, candidate_pool: int, text_search_config: str,
                 max_document_ratio: float):
        self.database = database
        self.vector_table = vector_table
        self.embeddings = embeddings
        self.fusion = fusion
        self.candidate_pool = candidate_pool
        self.text_search_config = text_search_config
        self.max_document_ratio = max_document_ratio

    def search(self, query: str, top_k: int) -> list[ScoredChunk]:
        vector = self.embeddings.embed_one(query)
        pool = max(self.candidate_pool, top_k)
        with self.database.transaction() as connection:
            store = PgVectorStore(connection, self.vector_table)
            semantic = store.similarity_search(vector, pool)
            lexical = store.lexical_search(query, vector, self.text_search_config, pool, self.max_document_ratio)
        return self.fusion.fuse([semantic, lexical])[:top_k]
