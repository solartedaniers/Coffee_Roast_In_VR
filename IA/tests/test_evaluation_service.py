import pytest

from app.evaluation.contracts import EvaluationRequest, EvaluationStatus, OperationMode
from app.evaluation.grader import CriterionGrade, GradingUnavailable
from app.evaluation.rubric import CriterionId
from app.evaluation.service import RoastEvaluationService, clip, rubric_score
from tests.test_evaluation_facts import trace
from tests.test_evaluation_prompt import RUBRIC


class FakeGrader:
    model = "openai/gpt-oss-120b"

    def __init__(self, levels=None, error=None, justifications=None):
        self.levels = levels or {}
        self.error = error
        self.justifications = justifications or {}
        self.prompts = []

    def grade(self, prompt):
        self.prompts.append(prompt)
        if self.error:
            raise self.error
        ids = prompt.schema["properties"]["criteria"]["required"]
        return {CriterionId(i): CriterionGrade(self.levels.get(i, 3), self.justifications.get(i, plain(i)))
                for i in ids}


def plain(criterion_id):
    return f"Justificación sobre {RUBRIC.criterion(CriterionId(criterion_id)).title.lower()}."


def evaluate(grader, **trace_changes):
    request = EvaluationRequest(trace=trace(**trace_changes))
    return RoastEvaluationService(RUBRIC, grader).evaluate(request)


@pytest.mark.parametrize("levels, expected", [([3, 3, 3], 100.0), ([3, 2, 1], 66.7), ([0], 0.0), ([1, 2], 50.0)])
def test_the_score_is_the_share_of_the_maximum(levels, expected):
    assert rubric_score(levels, 3) == expected


def test_grades_only_the_applicable_criteria_and_lists_the_rest():
    grader = FakeGrader({"DRYING_GAS": 3, "RISE_TO_FIRST_CRACK": 2, "GAS_AFTER_FIRST_CRACK": 1})

    response = evaluate(grader)

    assert response.status is EvaluationStatus.COMPLETED
    assert response.score == 66.7 and response.model == "openai/gpt-oss-120b"
    assert response.rubric_version == RUBRIC.rubric_version
    assert [(c.id, c.level, c.max_level) for c in response.criteria] == [
        (CriterionId.DRYING_GAS, 3, 3), (CriterionId.RISE_TO_FIRST_CRACK, 2, 3),
        (CriterionId.GAS_AFTER_FIRST_CRACK, 1, 3)]
    assert response.not_applicable == [CriterionId.ALERT_REACTION]


def test_auto_mode_is_not_applicable_without_calling_groq():
    grader = FakeGrader()

    response = evaluate(grader, operation_mode=OperationMode.AUTO)

    assert response.status is EvaluationStatus.NOT_APPLICABLE and response.score is None
    assert response.model is None and len(response.not_applicable) == 4
    assert grader.prompts == []


def test_a_groq_failure_reaches_the_caller():
    with pytest.raises(GradingUnavailable):
        evaluate(FakeGrader(error=GradingUnavailable("down")))


def test_clips_long_justifications():
    assert clip("abc", 5) == "abc"
    assert clip("una frase demasiado larga", 10) == "una frase…"
    assert len(clip("x" * 400, 300)) == 300


def test_the_player_never_sees_internal_fact_names():
    grader = FakeGrader({"DRYING_GAS": 0, "RISE_TO_FIRST_CRACK": 1}, justifications={
        "DRYING_GAS": "No estuviste en la franja (inBandPercent = 0) y la temperatura subió muy rápido.",
        "RISE_TO_FIRST_CRACK": "Tu longestStallSeconds fue alto.",
    })
    service = RoastEvaluationService(RUBRIC, grader)

    response = service.evaluate(EvaluationRequest(trace=trace()))

    texts = {criterion.id: criterion.justification for criterion in response.criteria}
    assert texts[CriterionId.DRYING_GAS] == "No estuviste en la franja y la temperatura subió muy rápido."
    assert texts[CriterionId.RISE_TO_FIRST_CRACK] == RUBRIC.render(
        RUBRIC.criterion(CriterionId.RISE_TO_FIRST_CRACK).fallback_justifications[1])
    assert texts[CriterionId.GAS_AFTER_FIRST_CRACK] == plain("GAS_AFTER_FIRST_CRACK")
    assert [criterion.level for criterion in response.criteria] == [0, 1, 3]
    assert dict(service.justifications.sources) == {"CLEANED": 1, "FALLBACK": 1, "MODEL": 1}
