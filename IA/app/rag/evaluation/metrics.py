"""Métricas de recuperación sobre el set de evaluación: hit@k y MRR.

Un acierto es cualquier fragmento recuperado cuyo (archivo, página) esté entre los esperados.
"""
from collections.abc import Sequence
from dataclasses import dataclass

MRR_CUTOFF = 10


@dataclass(frozen=True, order=True)
class PageRef:
    file_name: str
    page_number: int


def first_relevant_rank(retrieved: Sequence[PageRef], expected: set[PageRef]) -> int | None:
    """Posición (desde 1) del primer fragmento relevante, o None si no hay ninguno."""
    for rank, page in enumerate(retrieved, start=1):
        if page in expected:
            return rank
    return None


def hit_at(rank: int | None, k: int) -> bool:
    return rank is not None and rank <= k


def reciprocal_rank(rank: int | None, cutoff: int = MRR_CUTOFF) -> float:
    return 1.0 / rank if rank is not None and rank <= cutoff else 0.0


@dataclass(frozen=True)
class RankingSummary:
    cases: int
    hit_at: dict[int, float]
    mrr: float

    @classmethod
    def from_ranks(cls, ranks: Sequence[int | None], ks: Sequence[int]) -> "RankingSummary":
        if not ranks:
            return cls(0, {k: 0.0 for k in ks}, 0.0)
        total = len(ranks)
        return cls(
            cases=total,
            hit_at={k: sum(hit_at(rank, k) for rank in ranks) / total for k in ks},
            mrr=sum(reciprocal_rank(rank) for rank in ranks) / total,
        )
