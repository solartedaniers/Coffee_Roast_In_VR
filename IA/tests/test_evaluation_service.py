import pytest

from app.evaluation.contracts import EvaluationRequest, EvaluationStatus, OperationMode
from app.evaluation.grader import CriterionGrade, GradingUnavailable
from app.evaluation.rubric import CriterionId
from app.evaluation.service import RoastEvaluationService, clip, rubric_score
from tests.test_evaluation_facts import trace
from tests.test_evaluation_prompt import RUBRIC


class FakeGrader:
    model = "openai/gpt-oss-120b"

    def __init__(self, levels=None, error=None, justifications=None, rewrites=None, rewrite_error=None):
        self.levels = levels or {}
        self.error = error
        self.justifications = justifications or {}
        self.rewrites = rewrites or {}
        self.rewrite_error = rewrite_error
        self.prompts = []
        self.rewrite_prompts = []

    def rewrite(self, prompt):
        self.rewrite_prompts.append(prompt)
        if self.rewrite_error:
            raise self.rewrite_error
        return {CriterionId(i): self.rewrites[i] for i in prompt.schema["properties"]["criteria"]["required"]
                if i in self.rewrites}

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


def fallback(criterion_id, level):
    return RUBRIC.render(RUBRIC.criterion(criterion_id).fallback_justifications[level])


def test_asks_once_to_rewrite_only_the_unusable_justification_keeping_its_level():
    grader = FakeGrader({"RISE_TO_FIRST_CRACK": 2},
                        justifications={"RISE_TO_FIRST_CRACK": "Tu longestStallSeconds fue alto."},
                        rewrites={"RISE_TO_FIRST_CRACK": "La temperatura se quedó quieta 12 s antes del primer crack."})
    service = RoastEvaluationService(RUBRIC, grader)

    response = service.evaluate(EvaluationRequest(trace=trace()))

    rise = response.criteria[1]
    assert rise.level == 2 and rise.justification == "La temperatura se quedó quieta 12 s antes del primer crack."
    [prompt] = grader.rewrite_prompts
    assert prompt.schema["properties"]["criteria"]["required"] == ["RISE_TO_FIRST_CRACK"]
    assert "level" not in prompt.schema["properties"]["criteria"]["properties"]["RISE_TO_FIRST_CRACK"]["properties"]
    assert f"{RUBRIC.prompt_labels.assigned_level}: 2" in prompt.user
    assert "Tu longestStallSeconds fue alto." in prompt.user
    assert RUBRIC.rewrite_instruction in prompt.system
    assert response.score == 88.9
    assert dict(service.justifications.sources) == {"MODEL": 2, "REWRITTEN": 1}


def test_does_not_ask_for_a_rewrite_when_every_justification_is_usable():
    grader = FakeGrader()

    evaluate(grader)

    assert grader.rewrite_prompts == []


def test_falls_back_when_the_rewrite_is_still_unusable():
    grader = FakeGrader({"DRYING_GAS": 1}, justifications={"DRYING_GAS": "Tu inBandPercent fue bajo."},
                        rewrites={"DRYING_GAS": "Otra vez inBandPercent fue bajo en el secado."})

    response = evaluate(grader)

    assert response.criteria[0].level == 1
    assert response.criteria[0].justification == fallback(CriterionId.DRYING_GAS, 1)
    assert len(grader.rewrite_prompts) == 1


def test_falls_back_without_failing_the_evaluation_when_the_rewrite_is_unavailable():
    grader = FakeGrader({"GAS_AFTER_FIRST_CRACK": 0},
                        justifications={"GAS_AFTER_FIRST_CRACK": "raisedAboveStartingGas = true."},
                        rewrite_error=GradingUnavailable("quota"))

    response = evaluate(grader)

    assert response.status is EvaluationStatus.COMPLETED
    assert response.criteria[2].justification == fallback(CriterionId.GAS_AFTER_FIRST_CRACK, 0)
