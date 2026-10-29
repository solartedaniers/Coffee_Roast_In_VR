"""Validación de la rúbrica contra casos con nivel esperado (eval/evaluation/cases.json).

Mide dos cosas sobre el mismo calificador que usa el servicio: el acuerdo con el nivel esperado (exacto
y a un nivel de distancia) y la repetibilidad, corriendo cada caso varias veces con la misma traza.
"""
import json
import time
from collections.abc import Callable
from pathlib import Path

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

from app.evaluation.contracts import EvaluationRequest, EvaluationResponse, EvaluationStatus
from app.evaluation.grader import GradingUnavailable
from app.evaluation.rubric import CriterionId
from app.evaluation.service import RoastEvaluationService
from app.evaluation.synthetic_trace import SyntheticTrace

PERCENT_DECIMALS = 1


class ValidationCase(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, frozen=True)

    trace: SyntheticTrace
    expected: dict[CriterionId, int]
    expected_status: EvaluationStatus = EvaluationStatus.COMPLETED

    @classmethod
    def of(cls, raw: dict) -> "ValidationCase":
        fields = dict(raw)
        expected = fields.pop("expected")
        status = fields.pop("expectedStatus", EvaluationStatus.COMPLETED)
        return cls(trace=SyntheticTrace(**fields), expected=expected, expected_status=status)


def load_cases(path: Path) -> list[ValidationCase]:
    return [ValidationCase.of(raw) for raw in json.loads(path.read_text(encoding="utf-8"))["cases"]]


class ValidationRunner:
    def __init__(self, service: RoastEvaluationService, runs: int, pause_seconds: float = 0, retries: int = 0,
                 sleep: Callable[[float], None] = time.sleep):
        self.service = service
        self.runs = runs
        self.pause_seconds = pause_seconds
        self.retries = retries
        self.sleep = sleep

    def run(self, cases: list[ValidationCase]) -> dict:
        results = [self._run_case(case) for case in cases]
        return {"runs": self.runs, "summary": summarize(results, self.runs), "cases": results}

    def _run_case(self, case: ValidationCase) -> dict:
        responses = []
        failures = []
        for _ in range(self.runs):
            response = self._evaluate(case, failures)
            if response is not None:
                responses.append(response)
        return describe_case(case, responses, failures)

    def _evaluate(self, case: ValidationCase, failures: list[str]) -> EvaluationResponse | None:
        request = EvaluationRequest(trace=case.trace.trace())
        for attempt in range(self.retries + 1):
            try:
                response = self.service.evaluate(request)
                if response.status is EvaluationStatus.COMPLETED:
                    self.sleep(self.pause_seconds)
                return response
            except GradingUnavailable as error:
                failures.append(str(error))
                # Groq limita los tokens por minuto: se espera más en cada reintento.
                self.sleep(self.pause_seconds * (attempt + 2))
        return None


def describe_case(case: ValidationCase, responses: list[EvaluationResponse], failures: list[str]) -> dict:
    levels = {criterion_id: [] for criterion_id in case.expected}
    applicability_ok = True
    status_ok = True
    for response in responses:
        status_ok &= response.status is case.expected_status
        graded = {criterion.id: criterion.level for criterion in response.criteria}
        applicability_ok &= set(graded) == set(case.expected)
        for criterion_id in levels:
            if criterion_id in graded:
                levels[criterion_id].append(graded[criterion_id])
    return {
        "id": case.trace.id,
        "description": case.trace.description,
        "completedRuns": len(responses),
        "failedAttempts": len(failures),
        "errors": sorted(set(failures)),
        "statusMatches": status_ok and bool(responses),
        "applicableCriteriaMatch": applicability_ok and bool(responses),
        "scores": [response.score for response in responses],
        "criteria": [{
            "id": criterion_id,
            "expected": expected,
            "levels": levels[criterion_id],
            "exact": sum(level == expected for level in levels[criterion_id]),
            "withinOne": sum(abs(level - expected) <= 1 for level in levels[criterion_id]),
            "stable": len(set(levels[criterion_id])) == 1,
            "justifications": sorted({criterion.justification for response in responses
                                      for criterion in response.criteria if criterion.id == criterion_id}),
        } for criterion_id, expected in case.expected.items()],
    }


def percent(part: int, total: int) -> float | None:
    return None if total == 0 else round(100 * part / total, PERCENT_DECIMALS)


def summarize(results: list[dict], runs: int) -> dict:
    criteria = [criterion for result in results for criterion in result["criteria"] if criterion["levels"]]
    graded = sum(len(criterion["levels"]) for criterion in criteria)
    by_criterion = {}
    for criterion in criteria:
        entry = by_criterion.setdefault(criterion["id"], {"graded": 0, "exact": 0, "withinOne": 0})
        entry["graded"] += len(criterion["levels"])
        entry["exact"] += criterion["exact"]
        entry["withinOne"] += criterion["withinOne"]
    return {
        "cases": len(results),
        "gradedLevels": graded,
        "exactAgreementPercent": percent(sum(criterion["exact"] for criterion in criteria), graded),
        "withinOneAgreementPercent": percent(sum(criterion["withinOne"] for criterion in criteria), graded),
        "stableCriteriaPercent": percent(sum(criterion["stable"] for criterion in criteria), len(criteria)),
        "stableScoreCases": sum(len(set(result["scores"])) == 1 for result in results if result["scores"]),
        "statusMismatches": [result["id"] for result in results if not result["statusMatches"]],
        "applicabilityMismatches": [result["id"] for result in results if not result["applicableCriteriaMatch"]],
        "failedAttempts": sum(result["failedAttempts"] for result in results),
        "incompleteCases": [result["id"] for result in results if result["completedRuns"] < runs],
        "byCriterion": {criterion_id: entry | {
            "exactAgreementPercent": percent(entry["exact"], entry["graded"]),
            "withinOneAgreementPercent": percent(entry["withinOne"], entry["graded"]),
        } for criterion_id, entry in by_criterion.items()},
        "disagreements": [{"case": result["id"], "criterion": criterion["id"], "expected": criterion["expected"],
                           "levels": criterion["levels"]}
                          for result in results for criterion in result["criteria"]
                          if criterion["exact"] < len(criterion["levels"])],
    }
