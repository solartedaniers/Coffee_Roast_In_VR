"""Decide qué justificación ve el jugador por criterio: la del modelo (limpia si hace falta) o la de respaldo."""
from collections import Counter
from enum import StrEnum

from app.evaluation.facts import Facts
from app.evaluation.grader import CriterionGrade
from app.evaluation.justification_guard import JustificationGuard
from app.evaluation.rubric import CriterionId, Rubric


class JustificationSource(StrEnum):
    MODEL = "MODEL"
    CLEANED = "CLEANED"
    FALLBACK = "FALLBACK"


class PlayerJustifications:
    def __init__(self, rubric: Rubric):
        self.rubric = rubric
        self.guard = JustificationGuard(rubric)
        # Cuántas justificaciones salieron de cada fuente; la validación con Groq las reporta.
        self.sources: Counter[JustificationSource] = Counter()

    def resolve(self, grades: dict[CriterionId, CriterionGrade],
                facts: dict[CriterionId, Facts]) -> dict[CriterionId, str]:
        texts = {}
        for criterion_id, grade in grades.items():
            reviewed = self.guard.review(grade.justification, facts.get(criterion_id))
            if reviewed.text is None:
                texts[criterion_id] = self.fallback(criterion_id, grade.level)
                self.sources[JustificationSource.FALLBACK] += 1
            else:
                texts[criterion_id] = reviewed.text
                self.sources[JustificationSource.CLEANED if reviewed.cleaned else JustificationSource.MODEL] += 1
        return texts

    def fallback(self, criterion_id: CriterionId, level: int) -> str:
        return self.rubric.render(self.rubric.criterion(criterion_id).fallback_justifications[level])
