from app.container import build_chunk_search
from app.rag.chunk_filters import FilteredChunkSearch, MinimumSimilarityFilter
from app.rag.chunk_search import SemanticChunkSearch
from app.rag.vector_store import ScoredChunk


def chunk(text, score=0.5, file_name="a.pdf", page=1):
    return ScoredChunk(text, {"file_name": file_name, "page_number": page}, score)


def texts(chunks):
    return [item.text for item in chunks]


class FakeSearch:
    def __init__(self, chunks):
        self.chunks = chunks
        self.requested = None

    def search(self, query, top_k):
        self.requested = top_k
        return self.chunks[:top_k]


def test_drops_chunks_below_the_minimum_similarity():
    kept = MinimumSimilarityFilter(0.7).apply([chunk("a", 0.9), chunk("b", 0.7), chunk("c", 0.69)])

    assert texts(kept) == ["a", "b"]


def test_asks_for_more_candidates_and_cuts_after_filtering():
    inner = FakeSearch([chunk("a", 0.9), chunk("b", 0.1), chunk("c", 0.8), chunk("d", 0.75)])

    found = FilteredChunkSearch(inner, [MinimumSimilarityFilter(0.7)], candidate_pool=20).search("q", 2)

    assert inner.requested == 20
    assert texts(found) == ["a", "c"]


def test_may_return_nothing_when_no_chunk_is_relevant():
    found = FilteredChunkSearch(FakeSearch([chunk("a", 0.2)]), [MinimumSimilarityFilter(0.7)], 20).search("q", 4)

    assert found == []


def test_the_container_only_wraps_the_search_when_a_filter_is_on(settings):
    assert isinstance(build_chunk_search(settings, object(), object()), SemanticChunkSearch)

    filtered = build_chunk_search(settings.model_copy(update={"rag_min_similarity": 0.7}), object(), object())

    assert isinstance(filtered, FilteredChunkSearch)
    assert [type(item) for item in filtered.filters] == [MinimumSimilarityFilter]
    assert filtered.candidate_pool == 20
