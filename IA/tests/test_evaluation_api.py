from fastapi.testclient import TestClient

from app.api import create_app
from app.container import Services
from app.evaluation.grader import GradingUnavailable
from app.evaluation.service import RoastEvaluationService
from app.feedback.cache import FeedbackCache
from tests.test_api import FakeFeedback, FakeIngestion
from tests.test_evaluation_facts import trace
from tests.test_evaluation_prompt import RUBRIC
from tests.test_evaluation_service import FakeGrader


def client_with(grader) -> TestClient:
    services = Services(FakeFeedback(""), FakeIngestion([]), FeedbackCache(0, 0), schema=None,
                        evaluation=RoastEvaluationService(RUBRIC, grader))
    return TestClient(create_app(services, ensure_schema=False))


def body(**changes):
    return {"trace": trace(**changes).model_dump(mode="json", by_alias=True)}


def test_answers_the_evaluation_in_camel_case():
    response = client_with(FakeGrader({"DRYING_GAS": 2})).post("/api/v1/evaluation", json=body())

    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "COMPLETED" and data["rubricVersion"] == RUBRIC.rubric_version
    assert data["score"] == 88.9 and data["notApplicable"] == ["ALERT_REACTION"]
    assert data["criteria"][0] == {"id": "DRYING_GAS", "level": 2, "maxLevel": 3,
                                   "justification": "Justificación sobre manejo del gas en el secado."}


def test_answers_503_when_groq_fails_so_the_backend_retries():
    client = client_with(FakeGrader(error=GradingUnavailable("quota")))

    response = client.post("/api/v1/evaluation", json=body())

    assert response.status_code == 503


def test_rejects_an_invalid_trace_with_400():
    data = body()
    data["trace"]["version"] = 9

    assert client_with(FakeGrader()).post("/api/v1/evaluation", json=data).status_code == 400
