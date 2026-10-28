"""Filtros sobre los fragmentos recuperados, antes de que lleguen al prompt."""
from collections.abc import Sequence
from typing import Protocol

from app.rag.chunk_search import ChunkSearch
from app.rag.pdf_reader import FILE_NAME_KEY, PAGE_NUMBER_KEY
from app.rag.vector_store import ScoredChunk


class ChunkFilter(Protocol):
    def apply(self, chunks: Sequence[ScoredChunk]) -> list[ScoredChunk]: ...


class DuplicateTextFilter:
    """Deja solo la primera aparición de cada texto, sin distinguir espacios ni mayúsculas."""

    def apply(self, chunks: Sequence[ScoredChunk]) -> list[ScoredChunk]:
        seen: set[str] = set()
        unique = []
        for chunk in chunks:
            key = " ".join(chunk.text.split()).casefold()
            if key not in seen:
                seen.add(key)
                unique.append(chunk)
        return unique


class PageQuotaFilter:
    """Como máximo `max_per_page` fragmentos de una misma página, para variar las fuentes."""

    def __init__(self, max_per_page: int):
        if max_per_page < 1:
            raise ValueError("The page quota must be at least 1")
        self.max_per_page = max_per_page

    def apply(self, chunks: Sequence[ScoredChunk]) -> list[ScoredChunk]:
        counts: dict[tuple[object, object], int] = {}
        kept = []
        for chunk in chunks:
            page = (chunk.metadata.get(FILE_NAME_KEY), chunk.metadata.get(PAGE_NUMBER_KEY))
            counts[page] = counts.get(page, 0) + 1
            if counts[page] <= self.max_per_page:
                kept.append(chunk)
        return kept


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
