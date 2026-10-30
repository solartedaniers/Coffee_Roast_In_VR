"""Carga el set de evaluación (IA/eval/rag_questions.json).

Para ampliarlo basta con agregar casos al JSON; no hay que tocar código. Lo que aún no valido va
marcado con "draft": true, en la pregunta completa o en una página esperada, y se reporta aparte.
Con "group" una pregunta se resume además en su grupo (por ejemplo, según de qué PDFs sale).
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
    draft_pages: frozenset[PageRef] = frozenset()
    draft: bool = False
    group: str | None = None

    @property
    def expected_with_drafts(self) -> frozenset[PageRef]:
        return self.expected | self.draft_pages


class EvalSetLoader:
    """`absent_local_files`: PDFs locales que pueden faltar (en el CI no están). Sus páginas no se pueden
    revisar, así que solo se aceptan si no se exige tener todos los PDFs, como al medir."""

    def __init__(self, catalog: PdfCatalog, absent_local_files: frozenset[str] = frozenset(),
                 require_all_files: bool = True):
        self.catalog = catalog
        self.absent_local_files = absent_local_files
        self.require_all_files = require_all_files

    def load(self, path: Path) -> list[EvalCase]:
        raw = json.loads(path.read_text(encoding="utf-8"))
        cases = [self._parse(item) for item in raw["cases"]]
        self._validate(cases)
        return cases

    @staticmethod
    def _parse(item: dict) -> EvalCase:
        pages = item.get("expected", [])
        expected = frozenset(PageRef(page["file"], int(page["page"])) for page in pages if not page.get("draft"))
        draft_pages = frozenset(PageRef(page["file"], int(page["page"])) for page in pages if page.get("draft"))
        return EvalCase(item["id"], CaseKind(item["kind"]), item["question"], expected, draft_pages,
                        bool(item.get("draft", False)), item.get("group"))

    def _validate(self, cases: list[EvalCase]) -> None:
        ids = [case.id for case in cases]
        duplicated = sorted({case_id for case_id in ids if ids.count(case_id) > 1})
        if duplicated:
            raise InvalidEvalSet(f"Duplicated case ids: {duplicated}")
        for case in cases:
            if case.kind is CaseKind.PLAYER and not case.expected:
                raise InvalidEvalSet(f"{case.id}: a player question needs confirmed expected pages")
            if case.kind is not CaseKind.PLAYER and case.expected_with_drafts:
                raise InvalidEvalSet(f"{case.id}: only player questions have expected pages")
            if case.draft and case.draft_pages:
                raise InvalidEvalSet(f"{case.id}: a draft question cannot also have draft pages")
            missing = [page for page in case.expected_with_drafts
                       if not self._can_skip(page) and not self.catalog.contains_page(page.file_name, page.page_number)]
            if missing:
                raise InvalidEvalSet(f"{case.id}: expected pages not found in rag-docs: {missing}")

    def _can_skip(self, page: PageRef) -> bool:
        return not self.require_all_files and page.file_name in self.absent_local_files
