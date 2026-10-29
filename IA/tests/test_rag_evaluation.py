import json

import pytest

from app.config import SERVICE_ROOT
from app.rag.citations import Distribution, load_document_sources
from app.rag.evaluation.__main__ import DEFAULT_CASES, rag_configuration
from app.rag.evaluation.eval_set import CaseKind, EvalCase, EvalSetLoader, InvalidEvalSet
from app.rag.evaluation.evaluator import RetrievalEvaluator, summarize
from app.rag.evaluation.metrics import PageRef
from app.rag.pdf_catalog import PdfCatalog
from app.rag.vector_store import ScoredChunk
from tests.support.pdf_factory import write_text_pdf


@pytest.fixture
def catalog(tmp_path) -> PdfCatalog:
    write_text_pdf(tmp_path / "a.pdf", ["uno", "dos"])
    return PdfCatalog(tmp_path)


def write_cases(path, cases):
    path.write_text(json.dumps({"cases": cases}), encoding="utf-8")
    return path


def test_loads_the_cases_with_their_expected_pages(catalog, tmp_path):
    path = write_cases(tmp_path / "set.json", [
        {"id": "P1", "kind": "player", "question": "¿Qué?", "expected": [{"file": "a.pdf", "page": 2}]},
        {"id": "N1", "kind": "negative", "question": "¿Nada?", "expected": []},
    ])

    cases = EvalSetLoader(catalog).load(path)

    assert cases[0] == EvalCase("P1", CaseKind.PLAYER, "¿Qué?", frozenset({PageRef("a.pdf", 2)}))
    assert cases[1].kind is CaseKind.NEGATIVE


@pytest.mark.parametrize("cases", [
    [{"id": "P1", "kind": "player", "question": "q", "expected": [{"file": "a.pdf", "page": 3}]}],
    [{"id": "P1", "kind": "player", "question": "q", "expected": [{"file": "b.pdf", "page": 1}]}],
    [{"id": "P1", "kind": "player", "question": "q", "expected": []}],
    [{"id": "N1", "kind": "negative", "question": "q", "expected": [{"file": "a.pdf", "page": 1}]}],
    [{"id": "N1", "kind": "negative", "question": "q"}, {"id": "N1", "kind": "negative", "question": "r"}],
])
def test_rejects_invalid_sets(catalog, tmp_path, cases):
    with pytest.raises(InvalidEvalSet):
        EvalSetLoader(catalog).load(write_cases(tmp_path / "set.json", cases))


def test_an_absent_local_pdf_is_accepted_only_when_not_every_file_is_required(catalog, tmp_path):
    path = write_cases(tmp_path / "set.json", [
        {"id": "P1", "kind": "player", "question": "q", "expected": [{"file": "local-b.pdf", "page": 4}]},
    ])
    absent = frozenset({"local-b.pdf"})

    assert len(EvalSetLoader(catalog, absent, require_all_files=False).load(path)) == 1
    with pytest.raises(InvalidEvalSet):
        EvalSetLoader(catalog, absent).load(path)


def test_the_committed_set_is_valid():
    docs = SERVICE_ROOT / "rag-docs"
    catalog = PdfCatalog(docs)
    sources = load_document_sources(docs / "document_titles.json")
    absent_local = frozenset(name for name, source in sources.items()
                             if source.distribution is Distribution.LOCAL and name not in catalog.file_names())
    cases = EvalSetLoader(catalog, absent_local, require_all_files=False).load(DEFAULT_CASES)

    kinds = [case.kind for case in cases]
    assert (kinds.count(CaseKind.PLAYER), kinds.count(CaseKind.NEGATIVE), kinds.count(CaseKind.SESSION)) == (43, 7, 4)
    assert not any(case.draft or case.draft_pages for case in cases)


class FakeSearch:
    def __init__(self, chunks):
        self.chunks = chunks
        self.calls = []

    def search(self, query, top_k):
        self.calls.append((query, top_k))
        return self.chunks[:top_k]


def chunk(file_name, page, text="texto"):
    return ScoredChunk(text, {"file_name": file_name, "page_number": page}, 0.5)


def ticking_clock():
    ticks = iter(range(100))
    return lambda: next(ticks) / 1000


def test_finds_the_rank_of_the_first_expected_page():
    search = FakeSearch([chunk("a.pdf", 1, "x"), chunk("a.pdf", 2, "y"), chunk("a.pdf", 2, "z")])
    case = EvalCase("P1", CaseKind.PLAYER, "¿Qué?", frozenset({PageRef("a.pdf", 2)}))

    [result] = RetrievalEvaluator(search, depth=10, production_top_k=2, clock=ticking_clock()).run([case])

    assert search.calls == [("¿Qué?", 10)]
    assert result.first_relevant_rank == 2
    assert result.returned_in_top_k == 2
    assert result.latency_ms == 1.0
    assert [(r.file_name, r.page_number) for r in result.retrieved] == [("a.pdf", 1), ("a.pdf", 2), ("a.pdf", 2)]


def test_counts_repeated_texts_only_in_what_the_prompt_would_get():
    search = FakeSearch([chunk("a.pdf", 1, "igual"), chunk("a.pdf", 1, "igual "), chunk("a.pdf", 2, "igual")])
    case = EvalCase("N1", CaseKind.NEGATIVE, "q", frozenset())

    [result] = RetrievalEvaluator(search, depth=10, production_top_k=2).run([case])

    assert result.repeated_texts_in_top_k == 1


def test_summarizes_each_kind_separately():
    search = FakeSearch([chunk("a.pdf", 1)])
    cases = [EvalCase("P1", CaseKind.PLAYER, "q", frozenset({PageRef("a.pdf", 1)})),
             EvalCase("P2", CaseKind.PLAYER, "q", frozenset({PageRef("a.pdf", 9)})),
             EvalCase("N1", CaseKind.NEGATIVE, "q", frozenset())]

    summary = summarize(RetrievalEvaluator(search, depth=10, production_top_k=4).run(cases), production_top_k=4)

    assert summary["player"]["hit_at_4"] == 0.5
    assert summary["player"]["mrr_at_10"] == 0.5
    assert summary["player"]["misses_at_top_k"] == ["P2"]
    assert summary["negative"] == {"cases": 1, "cases_with_context": 1, "mean_returned": 1}
    assert summary["session"]["cases"] == 0


def test_reports_draft_questions_and_draft_pages_apart_from_the_validated_set(catalog, tmp_path):
    path = write_cases(tmp_path / "set.json", [
        {"id": "P1", "kind": "player", "question": "q",
         "expected": [{"file": "a.pdf", "page": 1}, {"file": "a.pdf", "page": 2, "draft": True}]},
        {"id": "N1", "kind": "player", "question": "q", "draft": True, "expected": [{"file": "a.pdf", "page": 2}]},
        {"id": "NN1", "kind": "negative", "question": "q", "draft": True},
    ])
    cases = EvalSetLoader(catalog).load(path)
    search = FakeSearch([chunk("a.pdf", 2), chunk("a.pdf", 1)])

    summary = summarize(RetrievalEvaluator(search, depth=10, production_top_k=4).run(cases), production_top_k=4)

    assert (summary["player"]["cases"], summary["player"]["mrr_at_10"]) == (1, 0.5)
    assert summary["groups"]["confirmed_with_draft_pages"]["mrr_at_10"] == 1.0
    assert (summary["groups"]["draft_questions"]["cases"], summary["groups"]["draft_questions"]["hit_at_1"]) == (1, 1.0)
    assert summary["groups"]["total"]["cases"] == 2
    assert (summary["negative"]["cases"], summary["negative_draft"]["cases"]) == (0, 1)


def test_a_draft_question_cannot_also_have_draft_pages(catalog, tmp_path):
    path = write_cases(tmp_path / "set.json", [
        {"id": "N1", "kind": "player", "question": "q", "draft": True,
         "expected": [{"file": "a.pdf", "page": 1}, {"file": "a.pdf", "page": 2, "draft": True}]},
    ])

    with pytest.raises(InvalidEvalSet):
        EvalSetLoader(catalog).load(path)


def test_rejects_a_top_k_deeper_than_the_evaluation():
    with pytest.raises(ValueError):
        RetrievalEvaluator(FakeSearch([]), depth=4, production_top_k=5)


def test_records_only_rag_settings_without_local_paths(settings):
    configuration = rag_configuration(settings)

    assert configuration["rag_top_k"] == 4
    assert "rag_docs_dir" not in configuration
    assert all(name.startswith("rag_") for name in configuration)
