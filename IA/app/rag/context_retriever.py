import logging

from app.contracts import RoastFeedbackRequest
from app.domain import KnowledgeLevel
from app.rag.chunk_search import ChunkSearch
from app.rag.citations import CitationResolver, RetrievedPassage
from app.rag.query_builder import RagQueryBuilder
from app.rag.vector_store import FILE_NAME_METADATA_KEY, ScoredChunk

log = logging.getLogger(__name__)
PREVIEW_CHARS = 80


class RagContextRetriever:
    """Fragmentos de rag-docs/ más parecidos a la sesión, para agregarlos al prompt."""

    def __init__(self, chunk_search: ChunkSearch, query_builder: RagQueryBuilder, top_k: int,
                 citations: CitationResolver):
        self.chunk_search = chunk_search
        self.query_builder = query_builder
        self.top_k = top_k
        self.citations = citations

    def find_relevant_chunks(self, session: RoastFeedbackRequest, knowledge_level: KnowledgeLevel | None) -> list[str]:
        """Solo los textos: es lo que recibe el prompt de la retroalimentación."""
        return [passage.text for passage in self.find_relevant_passages(session, knowledge_level)]

    def find_relevant_passages(self, session: RoastFeedbackRequest,
                               knowledge_level: KnowledgeLevel | None) -> list[RetrievedPassage]:
        """Textos con su cita validada (archivo, página y título), para el asistente de voz."""
        # El RAG es un extra sobre la retroalimentación: si falla (base caída, índice vacío,
        # Ollama apagado) se sigue sin contexto, nunca se rompe la respuesta.
        try:
            chunks = self.chunk_search.search(self.query_builder.build(session, knowledge_level), self.top_k)
        except Exception as error:
            log.warning("RAG retrieval failed, continuing without extra context: %s", error)
            return []
        self._log_retrieved(chunks)
        return self.citations.passages(chunks)

    @staticmethod
    def _log_retrieved(chunks: list[ScoredChunk]) -> None:
        # Siempre visible (no solo en debug) para verificar el RAG desde los logs.
        if not chunks:
            log.info("RAG retrieval returned 0 chunks (empty index or no match above threshold)")
        for chunk in chunks:
            preview = chunk.text if len(chunk.text) <= PREVIEW_CHARS else chunk.text[:PREVIEW_CHARS] + "..."
            log.info('RAG retrieved chunk from file=%s score=%s: "%s"',
                     chunk.metadata.get(FILE_NAME_METADATA_KEY, "unknown"), chunk.score, preview)
