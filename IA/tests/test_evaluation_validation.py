import pytest

from app.config import SERVICE_ROOT
from app.evaluation.contracts import EvaluationRequest, EvaluationStatus
from app.evaluation.facts import DecisionFactsExtractor
from app.evaluation.grader import GradingUnavailable
from app.evaluation.service import RoastEvaluationService
from app.evaluation.validation import ValidationRunner, describe_case, load_cases, summarize
from tests.test_evaluation_prompt import RUBRIC
from tests.test_evaluation_service import FakeGrader

CASES = load_cases(SERVICE_ROOT / "eval" / "evaluation" / "cases.json")
BY_ID = {case.trace.id: case for case in CASES}


# Sin llamar a Groq: los hechos de cada caso tienen que dar exactamente los criterios que el caso espera,
# si no, un desacuerdo en la validación sería culpa del caso y no del modelo.
@pytest.mark.parametrize("case", CASES, ids=lambda case: case.trace.id)
def test_each_case_applies_exactly_the_expected_criteria(case):
    response = RoastEvaluationService(RUBRIC, FakeGrader()).evaluate(EvaluationRequest(trace=case.trace.trace()))

    assert response.status is case.expected_status
    assert {criterion.id for criterion in response.criteria} == set(case.expected)
    assert all(0 <= level <= RUBRIC.max_level for level in case.expected.values())


def test_the_cases_cover_every_level_of_every_criterion_but_alerts():
    seen = {(criterion_id, level) for case in CASES for criterion_id, level in case.expected.items()}

    for criterion in RUBRIC.criteria:
        levels = {level for criterion_id, level in seen if criterion_id is criterion.id}
        assert levels >= ({0, 3} if criterion.id.value == "ALERT_REACTION" else {0, 1, 2, 3}), criterion.id


def test_the_facts_behind_some_expected_levels():
    extractor = DecisionFactsExtractor(RUBRIC)
    stall = extractor.extract(BY_ID["stall_medium"].trace.trace())

    assert stall["RISE_TO_FIRST_CRACK"]["longestStallSeconds"] == 20
    drying = extractor.extract(BY_ID["drying_slow_late_fix"].trace.trace())["DRYING_GAS"]
    assert drying["inBandPercent"] < RUBRIC.parameters.drying_in_band_low
    assert drying["slowestCorrectionSeconds"] > RUBRIC.parameters.drying_slow_correction_seconds


class ScriptedGrader(FakeGrader):
    """Devuelve en cada llamada los niveles de la lista; cuando se acaba, repite el último."""

    def __init__(self, scripted, error_first=0):
        super().__init__()
        self.scripted = scripted
        self.errors_left = error_first

    def grade(self, prompt):
        if self.errors_left:
            self.errors_left -= 1
            raise GradingUnavailable("Groq 429")
        self.levels = self.scripted[min(len(self.prompts), len(self.scripted) - 1)]
        return super().grade(prompt)


def runner(grader, runs=3, retries=0):
    return ValidationRunner(RoastEvaluationService(RUBRIC, grader), runs, pause_seconds=2, retries=retries,
                            sleep=lambda seconds: None)


def test_measures_agreement_and_repeatability():
    good = BY_ID["good"]
    grader = ScriptedGrader([
        {"DRYING_GAS": 3, "RISE_TO_FIRST_CRACK": 3, "GAS_AFTER_FIRST_CRACK": 3},
        {"DRYING_GAS": 3, "RISE_TO_FIRST_CRACK": 2, "GAS_AFTER_FIRST_CRACK": 3},
        {"DRYING_GAS": 3, "RISE_TO_FIRST_CRACK": 1, "GAS_AFTER_FIRST_CRACK": 3},
    ])

    report = runner(grader).run([good])

    summary = report["summary"]
    assert summary["gradedLevels"] == 9
    assert summary["exactAgreementPercent"] == 77.8
    assert summary["withinOneAgreementPercent"] == 88.9
    assert summary["stableCriteriaPercent"] == 66.7
    assert summary["stableScoreCases"] == 0
    assert summary["byCriterion"]["RISE_TO_FIRST_CRACK"]["exactAgreementPercent"] == 33.3
    assert summary["disagreements"] == [{"case": "good", "criterion": "RISE_TO_FIRST_CRACK", "expected": 3,
                                         "levels": [3, 2, 1]}]
    [case] = report["cases"]
    assert case["scores"] == [100.0, 88.9, 77.8]
    assert case["statusMatches"] and case["applicableCriteriaMatch"]


def test_auto_is_checked_by_status_and_never_calls_the_model():
    grader = ScriptedGrader([{}])

    report = runner(grader).run([BY_ID["auto"]])

    assert grader.prompts == []
    assert report["cases"][0]["statusMatches"] is True
    assert report["summary"]["gradedLevels"] == 0
    assert report["summary"]["exactAgreementPercent"] is None
    assert report["summary"]["incompleteCases"] == []


def test_retries_when_groq_is_unavailable_and_reports_what_could_not_run():
    retried = runner(ScriptedGrader([{}], error_first=1), runs=1, retries=1).run([BY_ID["raw"]])
    assert retried["cases"][0]["completedRuns"] == 1
    assert retried["summary"]["failedAttempts"] == 1
    assert retried["summary"]["incompleteCases"] == []

    failed = runner(ScriptedGrader([{}], error_first=5), runs=2, retries=1).run([BY_ID["raw"]])
    assert failed["cases"][0]["completedRuns"] == 0
    assert failed["cases"][0]["errors"] == ["Groq 429"]
    assert failed["summary"]["incompleteCases"] == ["raw"]
    assert failed["summary"]["statusMismatches"] == ["raw"]


def test_a_wrong_expected_status_is_reported():
    report = runner(ScriptedGrader([{}]), runs=1).run([BY_ID["auto"].model_copy(
        update={"expected_status": EvaluationStatus.COMPLETED})])

    assert report["summary"]["statusMismatches"] == ["auto"]


def test_reports_where_each_justification_came_from_and_none_has_internal_names():
    grader = FakeGrader(justifications={
        "DRYING_GAS": "Mantuviste un buen ritmo de subida todo el secado (inBandPercent = 100).",
        "RISE_TO_FIRST_CRACK": "Tu longestStallSeconds fue cero.",
    })

    report = runner(grader, runs=2).run([BY_ID["good"]])

    summary = report["summary"]
    assert summary["justificationsWithInternalNames"] == []
    assert summary["justificationSources"] == {"CLEANED": 2, "FALLBACK": 2, "MODEL": 2}
    drying = report["cases"][0]["criteria"][0]
    assert drying["justifications"] == ["Mantuviste un buen ritmo de subida todo el secado."]
    assert drying["internalNameJustifications"] == []


def test_lists_any_justification_with_internal_names_that_reaches_the_player():
    case = BY_ID["good"]
    response = RoastEvaluationService(RUBRIC, FakeGrader()).evaluate(EvaluationRequest(trace=case.trace.trace()))

    result = describe_case(case, [response], [], lambda criterion_id, text: criterion_id == "RISE_TO_FIRST_CRACK")

    [flagged] = summarize([result], 1)["justificationsWithInternalNames"]
    assert flagged["case"] == "good" and flagged["criterion"] == "RISE_TO_FIRST_CRACK"


class QuotaRunsOutGrader(FakeGrader):
    """Califica bien las primeras llamadas y después responde 429 siempre, como al gastar la cuota diaria."""

    def __init__(self, good_calls, error="Groq: Remote service returned 429"):
        super().__init__()
        self.good_calls = good_calls
        self.failure = error
        self.calls = 0

    def grade(self, prompt):
        self.calls += 1
        if self.calls > self.good_calls:
            raise GradingUnavailable(self.failure)
        return super().grade(prompt)


def test_saves_the_partial_report_after_each_case():
    saved = []

    report = runner(FakeGrader(), runs=1).run([BY_ID["good"], BY_ID["raw"]], on_case=saved.append)

    assert [len(partial["cases"]) for partial in saved] == [1, 2]
    assert saved[-1] == report
    assert report["plannedCases"] == 2 and report["stoppedReason"] is None


def test_stops_and_keeps_what_it_measured_when_groq_runs_out_of_daily_quota():
    saved = []
    messages = []
    service = RoastEvaluationService(RUBRIC, QuotaRunsOutGrader(good_calls=3))
    validation = ValidationRunner(service, runs=2, pause_seconds=2, retries=2, sleep=lambda seconds: None,
                                  progress=messages.append)

    report = validation.run([BY_ID["good"], BY_ID["raw"], BY_ID["stall_medium"]], on_case=saved.append)

    assert report["stoppedReason"] == "GROQ_QUOTA_EXHAUSTED"
    assert [case["id"] for case in report["cases"]] == ["good", "raw"]
    assert [case["completedRuns"] for case in report["cases"]] == [2, 1]
    assert report["summary"]["gradedLevels"] == 7
    assert saved[-1] == report
    assert any("429" in message for message in messages)
    assert any(message.startswith("Groq sin cuota") for message in messages)


def test_a_timeout_does_not_stop_the_validation():
    service = RoastEvaluationService(RUBRIC, QuotaRunsOutGrader(good_calls=0, error="Groq: unreachable (ReadTimeout)"))

    report = ValidationRunner(service, runs=1, retries=1, sleep=lambda seconds: None).run(
        [BY_ID["good"], BY_ID["raw"]])

    assert report["stoppedReason"] is None
    assert [case["completedRuns"] for case in report["cases"]] == [0, 0]
