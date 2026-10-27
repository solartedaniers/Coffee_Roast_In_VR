from collections.abc import Sequence

from app.rag.vector_store import ScoredChunk


class ReciprocalRankFusion:
    """Combina varias listas ordenadas sumando 1 / (k + posición) de cada fragmento (RRF).

    Solo usa la posición, así que mezcla sin calibrar similitud coseno con rangos de texto.
    """

    def __init__(self, k: int):
        if k < 0:
            raise ValueError("The RRF constant must not be negative")
        self.k = k

    def fuse(self, rankings: Sequence[Sequence[ScoredChunk]]) -> list[ScoredChunk]:
        scores: dict[str, float] = {}
        chunks: dict[str, ScoredChunk] = {}
        for ranking in rankings:
            for position, chunk in enumerate(ranking, start=1):
                scores[chunk.chunk_id] = scores.get(chunk.chunk_id, 0.0) + 1.0 / (self.k + position)
                chunks.setdefault(chunk.chunk_id, chunk)
        # Empates por la similitud coseno, para que el orden no dependa de la lista de entrada.
        return sorted(chunks.values(), key=lambda chunk: (-scores[chunk.chunk_id], -chunk.score))
