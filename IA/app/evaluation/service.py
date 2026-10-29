from app.evaluation.contracts import (CriterionScore, EvaluationRequest, EvaluationResponse, EvaluationStatus,
                                      OperationMode)
from app.evaluation.facts import DecisionFactsExtractor
from app.evaluation.grader import CriterionGrade, GroqRubricGrader
from app.evaluation.prompt import RubricPromptBuilder
from app.evaluation.rubric import CriterionId, Rubric

SCORE_DECIMALS = 1
ELLIPSIS = "…"


def rubric_score(levels: list[int], max_level: int) -> float:
    """Porcentaje sobre los criterios que aplican; los que no aplican no entran al denominador."""
    return round(100 * sum(levels) / (max_level * len(levels)), SCORE_DECIMALS)


def clip(text: str, max_chars: int) -> str:
    return text if len(text) <= max_chars else text[:max_chars - len(ELLIPSIS)].rstrip() + ELLIPSIS


class RoastEvaluationService:
    def __init__(self, rubric: Rubric, grader: GroqRubricGrader):
        self.rubric = rubric
        self.extractor = DecisionFactsExtractor(rubric)
        self.prompts = RubricPromptBuilder(rubric)
        self.grader = grader

    def evaluate(self, request: EvaluationRequest) -> EvaluationResponse:
        """Lanza GradingUnavailable si Groq falla; el backend reintenta más tarde."""
        all_ids = [criterion.id for criterion in self.rubric.criteria]
        # En AUTO el gas lo mueve el simulador: las decisiones no son del jugador.
        facts = {} if request.trace.operation_mode is OperationMode.AUTO else self.extractor.extract(request.trace)
        not_applicable = [criterion_id for criterion_id in all_ids if criterion_id not in facts]
        if not facts:
            return EvaluationResponse(status=EvaluationStatus.NOT_APPLICABLE, rubric_version=self.rubric.rubric_version,
                                      model=None, score=None, criteria=[], not_applicable=not_applicable)
        grades = self.grader.grade(self.prompts.build(facts))
        criteria = [self._score(criterion_id, grades[criterion_id]) for criterion_id in facts]
        return EvaluationResponse(
            status=EvaluationStatus.COMPLETED,
            rubric_version=self.rubric.rubric_version,
            model=self.grader.model,
            score=rubric_score([criterion.level for criterion in criteria], self.rubric.max_level),
            criteria=criteria,
            not_applicable=not_applicable,
        )

    def _score(self, criterion_id: CriterionId, grade: CriterionGrade) -> CriterionScore:
        return CriterionScore(id=criterion_id, level=grade.level, max_level=self.rubric.max_level,
                              justification=clip(grade.justification,
                                                 self.rubric.parameters.justification_max_chars))
