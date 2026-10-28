import pytest

from app.rag.evaluation.metrics import PageRef, RankingSummary, first_relevant_rank, hit_at, reciprocal_rank

A1 = PageRef("a.pdf", 1)
A2 = PageRef("a.pdf", 2)
B1 = PageRef("b.pdf", 1)


def test_finds_the_position_of_the_first_relevant_page():
    assert first_relevant_rank([B1, A2, A1], {A1, A2}) == 2
    assert first_relevant_rank([B1], {A1}) is None
    assert first_relevant_rank([], {A1}) is None


def test_the_same_page_of_another_file_is_not_a_hit():
    assert first_relevant_rank([PageRef("b.pdf", 2)], {A2}) is None


def test_hit_at_k_includes_the_k_position():
    assert hit_at(3, 3)
    assert not hit_at(4, 3)
    assert not hit_at(None, 3)


def test_reciprocal_rank_ignores_hits_after_the_cutoff():
    assert reciprocal_rank(1) == 1.0
    assert reciprocal_rank(4) == 0.25
    assert reciprocal_rank(11) == 0.0
    assert reciprocal_rank(None) == 0.0


def test_summarizes_hits_and_mrr():
    summary = RankingSummary.from_ranks([1, 2, None, 5], ks=[1, 3, 5])

    assert summary.cases == 4
    assert summary.hit_at == {1: 0.25, 3: 0.5, 5: 0.75}
    assert summary.mrr == pytest.approx((1 + 0.5 + 0 + 0.2) / 4)


def test_an_empty_summary_is_zero():
    assert RankingSummary.from_ranks([], ks=[1]) == RankingSummary(0, {1: 0.0}, 0.0)
