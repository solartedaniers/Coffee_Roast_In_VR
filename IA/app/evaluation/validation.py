"""Validación de la rúbrica contra casos con nivel esperado (eval/evaluation/cases.json).

Mide sobre el mismo calificador que usa el servicio el acuerdo con el nivel esperado (exacto y a un nivel
de distancia) y la repetibilidad, corriendo cada caso varias veces con la misma traza. También revisa que
ninguna justificación que vería el jugador traiga nombres internos y de dónde salió cada una.
"""
import json
import time
from collections import Counter
from collections.abc import Callable
from pathlib import Path

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

from app.evaluation.contracts import EvaluationRequest, EvaluationResponse, EvaluationStatus
from app.evaluation.grader import GradingUnavailable
from app.evaluation.justification_guard import JustificationGuard
from app.evaluation.rubric import CriterionId
from app.evaluation.service import RoastEvaluationService
from app.evaluation.synthetic_trace import SyntheticTrace

PERCENT_DECIMALS = 1
QUOTA_STATUS = "429"
STOPPED_BY_QUOTA = "GROQ_QUOTA_EXHAUSTED"


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


class QuotaExhausted(Exception):
    """Una corrida agotó todos sus reintentos con 429: ya no es el límite por minuto sino el diario."""


class ValidationRunner:
    def __init__(self, service: RoastEvaluationService, runs: int, pause_seconds: float = 0, retries: int = 0,
                 sleep: Callable[[float], None] = time.sleep, progress: Callable[[str], None] = lambda message: None):
        self.service = service
        self.runs = runs
        self.pause_seconds = pause_seconds
        self.retries = retries
        self.sleep = sleep
        self.progress = progress
        self.guard = JustificationGuard(service.rubric)
        service.justifications.pace_rewrites(pause_seconds, sleep)

    def run(self, cases: list[ValidationCase], on_case: Callable[[dict], None] = lambda report: None) -> dict:
        """Llama a on_case con el reporte parcial después de cada caso, para no perder lo medido si se corta."""
        sources_before = Counter(self.service.justifications.sources)
        results = []
        stopped_reason = None
        for index, case in enumerate(cases, start=1):
            self.progress(f"[{index}/{len(cases)}] {case.trace.id}")
            result, exhausted = self._run_case(case)
            results.append(result)
            if exhausted:
                stopped_reason = STOPPED_BY_QUOTA
                self.progress(f"Groq sin cuota (429 en todos los reintentos): me detengo en {case.trace.id}")
            report = self._report(cases, results, sources_before, stopped_reason)
            on_case(report)
            if exhausted:
                break
        return self._report(cases, results, sources_before, stopped_reason)

    def _report(self, cases: list[ValidationCase], results: list[dict], sources_before: Counter,
                stopped_reason: str | None) -> dict:
        summary = summarize(results, self.runs)
        summary["justificationSources"] = dict(sorted(
            (self.service.justifications.sources - sources_before).items()))
        return {"runs": self.runs, "plannedCases": len(cases), "stoppedReason": stopped_reason,
                "summary": summary, "cases": list(results)}

    def _run_case(self, case: ValidationCase) -> tuple[dict, bool]:
        responses = []
        failures = []
        exhausted = False
        for run in range(1, self.runs + 1):
            try:
                response = self._evaluate(case, failures)
            except QuotaExhausted:
                exhausted = True
                break
            if response is not None:
                responses.append(response)
            self.progress(f"  corrida {run}/{self.runs}: {'ok' if response is not None else 'sin respuesta'}")
        facts = self.service.extractor.extract(case.trace.trace())
        result = describe_case(case, responses, failures,
                               lambda criterion_id, text: self.guard.has_internal_names(text, facts.get(criterion_id)))
        return result, exhausted

    def _evaluate(self, case: ValidationCase, failures: list[str]) -> EvaluationResponse | None:
        request = EvaluationRequest(trace=case.trace.trace())
        errors = []
        for attempt in range(self.retries + 1):
            try:
                response = self.service.evaluate(request)
                if response.status is EvaluationStatus.COMPLETED:
                    self.sleep(self.pause_seconds)
                return response
            except GradingUnavailable as error:
                errors.append(str(error))
                failures.append(str(error))
                self.progress(f"  error (intento {attempt + 1}/{self.retries + 1}): {error}")
                # Groq limita los tokens por minuto: se espera más en cada reintento.
                self.sleep(self.pause_seconds * (attempt + 2))
        if self.retries > 0 and all(QUOTA_STATUS in error for error in errors):
            raise QuotaExhausted(errors[-1])
        return None


def describe_case(case: ValidationCase, responses: list[EvaluationResponse], failures: list[str],
                  has_internal_names: Callable[[CriterionId, str], bool] = lambda criterion_id, text: False) -> dict:
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
    justifications = {criterion_id: sorted({criterion.justification for response in responses
                                            for criterion in response.criteria if criterion.id == criterion_id})
                      for criterion_id in case.expected}
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
            "justifications": justifications[criterion_id],
            "internalNameJustifications": [text for text in justifications[criterion_id]
                                           if has_internal_names(criterion_id, text)],
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
        "justificationsWithInternalNames": [{"case": result["id"], "criterion": criterion["id"], "text": text}
                                            for result in results for criterion in result["criteria"]
                                            for text in criterion["internalNameJustifications"]],
        "byCriterion": {criterion_id: entry | {
            "exactAgreementPercent": percent(entry["exact"], entry["graded"]),
            "withinOneAgreementPercent": percent(entry["withinOne"], entry["graded"]),
        } for criterion_id, entry in by_criterion.items()},
        "disagreements": [{"case": result["id"], "criterion": criterion["id"], "expected": criterion["expected"],
                           "levels": criterion["levels"]}
                          for result in results for criterion in result["criteria"]
                          if criterion["exact"] < len(criterion["levels"])],
    }
