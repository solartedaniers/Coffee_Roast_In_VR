"""Filtros sobre los fragmentos recuperados, antes de que lleguen al prompt."""
from collections.abc import Sequence
from typing import Protocol

from app.rag.chunk_search import ChunkSearch
from app.rag.pdf_reader import FILE_NAME_KEY, PAGE_NUMBER_KEY
from app.rag.vector_store import ScoredChunk


class ChunkFilter(Protocol):
    def apply(self, chunks: Sequence[ScoredChunk]) -> list[ScoredChunk]: ...


class MinimumSimilarityFilter:
    """Descarta los fragmentos con similitud coseno por debajo del umbral."""

    def __init__(self, min_similarity: float):
        self.min_similarity = min_similarity

    def apply(self, chunks: Sequence[ScoredChunk]) -> list[ScoredChunk]:
        return [chunk for chunk in chunks if chunk.score >= self.min_similarity]


class FilteredChunkSearch:
    """Pide más candidatos de los necesarios, aplica los filtros en orden y corta en top_k."""

    def __init__(self, inner: ChunkSearch, filters: Sequence[ChunkFilter], candidate_pool: int):
        self.inner = inner
        self.filters = list(filters)
        self.candidate_pool = candidate_pool

    def search(self, query: str, top_k: int) -> list[ScoredChunk]:
        chunks = self.inner.search(query, max(self.candidate_pool, top_k))
        for chunk_filter in self.filters:
            chunks = chunk_filter.apply(chunks)
        return chunks[:top_k]
