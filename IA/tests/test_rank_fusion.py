import pytest

from app.rag.rank_fusion import ReciprocalRankFusion
from app.rag.vector_store import ScoredChunk


def chunk(chunk_id, score=0.5):
    return ScoredChunk(chunk_id, {}, score, chunk_id)


def ids(chunks):
    return [item.chunk_id for item in chunks]


def test_a_chunk_found_by_both_searches_goes_first():
    fused = ReciprocalRankFusion(k=60).fuse([[chunk("a"), chunk("b")], [chunk("c"), chunk("b")]])

    assert ids(fused) == ["b", "a", "c"]


def test_adds_one_over_k_plus_position():
    fused = ReciprocalRankFusion(k=1).fuse([[chunk("a"), chunk("b"), chunk("c")], [chunk("c")]])

    # a = 1/2, b = 1/3, c = 1/4 + 1/2
    assert ids(fused) == ["c", "a", "b"]


def test_breaks_ties_by_cosine_similarity():
    fused = ReciprocalRankFusion(k=60).fuse([[chunk("a", 0.2)], [chunk("b", 0.9)]])

    assert ids(fused) == ["b", "a"]


def test_keeps_the_first_copy_of_each_chunk():
    fused = ReciprocalRankFusion(k=60).fuse([[chunk("a", 0.7)], [chunk("a", 0.1)]])

    assert [item.score for item in fused] == [0.7]


def test_rejects_a_negative_constant():
    with pytest.raises(ValueError):
        ReciprocalRankFusion(k=-1)
