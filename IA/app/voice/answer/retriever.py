import logging

from app.rag.chunk_search import ChunkSearch
from app.rag.citations import CitationResolver, RetrievedPassage

log = logging.getLogger(__name__)


class QuestionPassageRetriever:
    """Fragmentos de rag-docs/ para la pregunta del jugador, solo los que tienen cita validada.

    Busca con la pregunta tal cual, igual que el set de evaluación del RAG (eval/rag_questions.json).
    Un fragmento sin cita no entra: el asistente nunca puede apoyarse en una fuente que no existe.
    """

    def __init__(self, chunk_search: ChunkSearch, citations: CitationResolver, top_k: int):
        self.chunk_search = chunk_search
        self.citations = citations
        self.top_k = top_k

    def find(self, question: str) -> list[RetrievedPassage]:
        try:
            chunks = self.chunk_search.search(question, self.top_k)
        except Exception as error:
            # Sin RAG se sigue: el modelo puede responder con el estado del tueste o abstenerse.
            log.warning("Voice RAG retrieval failed, continuing without sources: %s", error)
            return []
        return [passage for passage in self.citations.passages(chunks) if passage.citation is not None]
