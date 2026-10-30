"""Corre el set de evaluación contra una búsqueda de fragmentos y resume los resultados.

Se busca hasta `depth` fragmentos para calcular MRR@10. Lo que vería el prompt son los primeros
`production_top_k`: los conteos de negativas, sesiones y repetidos se miden sobre esos.
"""
import statistics
import time
from collections.abc import Callable, Sequence
from dataclasses import asdict, dataclass

from app.rag.chunk_search import ChunkSearch
from app.rag.evaluation.eval_set import CaseKind, EvalCase
from app.rag.evaluation.metrics import PageRef, RankingSummary, first_relevant_rank, hit_at
from app.rag.pdf_reader import FILE_NAME_KEY, PAGE_NUMBER_KEY
from app.rag.vector_store import ScoredChunk

HIT_KS = (1, 3, 4, 5)
MILLIS_PER_SECOND = 1000
PREVIEW_CHARS = 120


@dataclass(frozen=True)
class RetrievedChunk:
    file_name: str
    page_number: int
    score: float
    preview: str

    @classmethod
    def from_scored(cls, chunk: ScoredChunk) -> "RetrievedChunk":
        page = chunk.metadata.get(PAGE_NUMBER_KEY)
        return cls(str(chunk.metadata.get(FILE_NAME_KEY, "")), int(page) if page is not None else 0,
                   round(float(chunk.score), 6), " ".join(chunk.text.split())[:PREVIEW_CHARS])

    @property
    def page(self) -> PageRef:
        return PageRef(self.file_name, self.page_number)


@dataclass(frozen=True)
class CaseResult:
    case_id: str
    kind: str
    first_relevant_rank: int | None
    latency_ms: float
    returned_in_top_k: int
    repeated_texts_in_top_k: int
    retrieved: list[RetrievedChunk]
    draft: bool = False
    # Contando también las páginas en borrador; igual a first_relevant_rank si no tiene.
    first_relevant_rank_with_drafts: int | None = None
    group: str | None = None


class RetrievalEvaluator:
    def __init__(self, search: ChunkSearch, depth: int, production_top_k: int,
                 clock: Callable[[], float] = time.perf_counter):
        if production_top_k > depth:
            raise ValueError("The production top_k cannot exceed the evaluation depth")
        self.search = search
        self.depth = depth
        self.production_top_k = production_top_k
        self.clock = clock

    def run(self, cases: Sequence[EvalCase]) -> list[CaseResult]:
        return [self._run_case(case) for case in cases]

    def _run_case(self, case: EvalCase) -> CaseResult:
        started = self.clock()
        chunks = self.search.search(case.question, self.depth)
        latency_ms = (self.clock() - started) * MILLIS_PER_SECOND
        top = chunks[:self.production_top_k]
        retrieved = [RetrievedChunk.from_scored(chunk) for chunk in chunks]
        texts = [" ".join(chunk.text.split()) for chunk in top]
        pages = [chunk.page for chunk in retrieved]
        return CaseResult(
            case_id=case.id,
            kind=case.kind.value,
            first_relevant_rank=first_relevant_rank(pages, set(case.expected)),
            latency_ms=round(latency_ms, 2),
            returned_in_top_k=len(top),
            repeated_texts_in_top_k=len(texts) - len(set(texts)),
            retrieved=retrieved,
            draft=case.draft,
            first_relevant_rank_with_drafts=first_relevant_rank(pages, set(case.expected_with_drafts)),
            group=case.group,
        )


def summarize(results: Sequence[CaseResult], production_top_k: int) -> dict:
    """`player` y `negative` son solo las preguntas validadas con sus páginas confirmadas, para que se
    puedan comparar con las mediciones anteriores. Lo que está en borrador va en `groups`."""
    confirmed = [result for result in results if not result.draft]
    by_kind = {kind: [result for result in confirmed if result.kind == kind.value] for kind in CaseKind}
    player_results = [result for result in results if result.kind == CaseKind.PLAYER.value]
    latencies = [result.latency_ms for result in results]
    return {
        "player": _ranking_summary(by_kind[CaseKind.PLAYER], production_top_k, with_drafts=False),
        "groups": {
            "confirmed_with_draft_pages": _ranking_summary(by_kind[CaseKind.PLAYER], production_top_k,
                                                           with_drafts=True),
            "draft_questions": _ranking_summary([result for result in player_results if result.draft],
                                                production_top_k, with_drafts=True),
            "total": _ranking_summary(player_results, production_top_k, with_drafts=True),
        },
        "by_group": {group: _ranking_summary([result for result in by_kind[CaseKind.PLAYER] if result.group == group],
                                             production_top_k, with_drafts=False)
                     for group in sorted({result.group for result in by_kind[CaseKind.PLAYER]} - {None})},
        "negative": _returned_summary(by_kind[CaseKind.NEGATIVE]),
        "negative_draft": _returned_summary([result for result in results
                                             if result.draft and result.kind == CaseKind.NEGATIVE.value]),
        "session": _returned_summary(by_kind[CaseKind.SESSION]),
        "repeated_texts_in_top_k": sum(result.repeated_texts_in_top_k for result in results),
        "latency_ms": {"p50": round(statistics.median(latencies), 1) if latencies else 0.0,
                       "max": round(max(latencies), 1) if latencies else 0.0},
    }


def _ranking_summary(results: Sequence[CaseResult], production_top_k: int, with_drafts: bool) -> dict:
    ranks = [result.first_relevant_rank_with_drafts if with_drafts else result.first_relevant_rank
             for result in results]
    summary = RankingSummary.from_ranks(ranks, HIT_KS)
    return {"cases": summary.cases, "mrr_at_10": round(summary.mrr, 4),
            **{f"hit_at_{k}": round(value, 4) for k, value in summary.hit_at.items()},
            "misses_at_top_k": [result.case_id for result, rank in zip(results, ranks, strict=True)
                                if not hit_at(rank, production_top_k)]}


def _returned_summary(results: Sequence[CaseResult]) -> dict:
    return {
        "cases": len(results),
        "cases_with_context": sum(result.returned_in_top_k > 0 for result in results),
        "mean_returned": round(statistics.mean([r.returned_in_top_k for r in results]), 2) if results else 0.0,
    }


def result_to_dict(result: CaseResult) -> dict:
    return asdict(result)
