"""Decide qué justificación ve el jugador por criterio.

Orden: la del modelo (limpia si hace falta); si no sirve, se le pide al modelo una sola vez que la reescriba
con el nivel fijo; si tampoco sirve, la de respaldo de la rúbrica. El nivel nunca cambia.
"""
import logging
from collections import Counter
from enum import StrEnum

from app.evaluation.facts import Facts
from app.evaluation.grader import GradingUnavailable, GroqRubricGrader
from app.evaluation.justification_guard import JustificationGuard
from app.evaluation.prompt import CriterionGrade, RubricPromptBuilder
from app.evaluation.rubric import CriterionId, Rubric

log = logging.getLogger(__name__)


class JustificationSource(StrEnum):
    MODEL = "MODEL"
    CLEANED = "CLEANED"
    REWRITTEN = "REWRITTEN"
    FALLBACK = "FALLBACK"


class PlayerJustifications:
    def __init__(self, rubric: Rubric, grader: GroqRubricGrader, prompts: RubricPromptBuilder):
        self.rubric = rubric
        self.grader = grader
        self.prompts = prompts
        self.guard = JustificationGuard(rubric)
        # Cuántas justificaciones salieron de cada fuente; la validación con Groq las reporta.
        self.sources: Counter[JustificationSource] = Counter()

    def resolve(self, grades: dict[CriterionId, CriterionGrade],
                facts: dict[CriterionId, Facts]) -> dict[CriterionId, str]:
        texts: dict[CriterionId, str] = {}
        unusable: dict[CriterionId, CriterionGrade] = {}
        for criterion_id, grade in grades.items():
            reviewed = self.guard.review(grade.justification, facts.get(criterion_id))
            if reviewed.text is None:
                unusable[criterion_id] = grade
            else:
                texts[criterion_id] = reviewed.text
                self.sources[JustificationSource.CLEANED if reviewed.cleaned else JustificationSource.MODEL] += 1
        if unusable:
            texts |= self._rewrite_or_fall_back(unusable, facts)
        return {criterion_id: texts[criterion_id] for criterion_id in grades}

    def _rewrite_or_fall_back(self, grades: dict[CriterionId, CriterionGrade],
                              facts: dict[CriterionId, Facts]) -> dict[CriterionId, str]:
        try:
            rewritten = self.grader.rewrite(self.prompts.rewrite(facts, grades))
        except GradingUnavailable as error:
            # Sin reescritura la evaluación sigue: el nivel ya está y el texto sale del respaldo.
            log.warning("Justification rewrite unavailable: %s", error)
            rewritten = {}
        texts = {}
        for criterion_id, grade in grades.items():
            reviewed = self.guard.review(rewritten.get(criterion_id, ""), facts.get(criterion_id))
            if reviewed.text is None:
                texts[criterion_id] = self.fallback(criterion_id, grade.level)
                self.sources[JustificationSource.FALLBACK] += 1
            else:
                texts[criterion_id] = reviewed.text
                self.sources[JustificationSource.REWRITTEN] += 1
        return texts

    def fallback(self, criterion_id: CriterionId, level: int) -> str:
        return self.rubric.render(self.rubric.criterion(criterion_id).fallback_justifications[level])
