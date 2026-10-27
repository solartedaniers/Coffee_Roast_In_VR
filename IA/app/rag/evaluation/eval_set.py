"""Carga el set de evaluación (IA/eval/rag_questions.json).

Para ampliarlo basta con agregar casos al JSON; no hay que tocar código.
"""
import json
from dataclasses import dataclass
from enum import StrEnum
from pathlib import Path

from app.rag.evaluation.metrics import PageRef
from app.rag.pdf_catalog import PdfCatalog


class CaseKind(StrEnum):
    PLAYER = "player"
    NEGATIVE = "negative"
    SESSION = "session"


class InvalidEvalSet(ValueError):
    """El set de evaluación no es válido."""


@dataclass(frozen=True)
class EvalCase:
    id: str
    kind: CaseKind
    question: str
    expected: frozenset[PageRef]


class EvalSetLoader:
    def __init__(self, catalog: PdfCatalog):
        self.catalog = catalog

    def load(self, path: Path) -> list[EvalCase]:
        raw = json.loads(path.read_text(encoding="utf-8"))
        cases = [self._parse(item) for item in raw["cases"]]
        self._validate(cases)
        return cases

    @staticmethod
    def _parse(item: dict) -> EvalCase:
        expected = frozenset(PageRef(page["file"], int(page["page"])) for page in item.get("expected", []))
        return EvalCase(item["id"], CaseKind(item["kind"]), item["question"], expected)

    def _validate(self, cases: list[EvalCase]) -> None:
        ids = [case.id for case in cases]
        duplicated = sorted({case_id for case_id in ids if ids.count(case_id) > 1})
        if duplicated:
            raise InvalidEvalSet(f"Duplicated case ids: {duplicated}")
        for case in cases:
            if case.kind is CaseKind.PLAYER and not case.expected:
                raise InvalidEvalSet(f"{case.id}: a player question needs expected pages")
            if case.kind is not CaseKind.PLAYER and case.expected:
                raise InvalidEvalSet(f"{case.id}: only player questions have expected pages")
            missing = [page for page in case.expected
                       if not self.catalog.contains_page(page.file_name, page.page_number)]
            if missing:
                raise InvalidEvalSet(f"{case.id}: expected pages not found in rag-docs: {missing}")
