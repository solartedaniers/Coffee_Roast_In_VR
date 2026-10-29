import pytest
from fastapi.testclient import TestClient

from app.api import create_app
from app.config import Settings
from app.container import Services, build_services, provider_configuration
from app.contracts import FileResult, IngestionStatus, RagIngestionSummaryResponse
from app.domain import KnowledgeLevel
from app.feedback.cache import FeedbackCache

SESSION_JSON = {
    "result": "PERFECT", "qualityScore": 92, "chargeTemperature": 190.0, "targetTemperature": 200.0,
    "finalTemperature": 200.0, "totalDurationSeconds": 780, "firstCrackReached": True,
    "developmentTimeSeconds": 156, "knowledgeLevel": "BEGINNER",
}


class FakeFeedback:
    def __init__(self, answer):
        self.answer = answer
        self.sessions = []

    def generate_feedback(self, session):
        self.sessions.append(session)
        return self.answer


class FakeIngestion:
    def __init__(self, files):
        self.files = files

    def ingest_all(self):
        return RagIngestionSummaryResponse.from_files(self.files)


def client_for(feedback=None, files=(), cache=None) -> TestClient:
    services = Services(feedback or FakeFeedback("Buen tueste."), FakeIngestion(list(files)),
                        cache or FeedbackCache(60, 10), schema=None)
    return TestClient(create_app(services, ensure_schema=False))


def test_generates_feedback_with_the_level_sent_by_the_backend():
    feedback = FakeFeedback("Buen tueste.")

    response = client_for(feedback).post("/api/v1/feedback", json=SESSION_JSON)

    assert response.status_code == 200
    assert response.json() == {"feedback": "Buen tueste."}
    assert feedback.sessions[0].knowledge_level is KnowledgeLevel.BEGINNER


def test_answers_null_feedback_when_no_provider_is_available():
    response = client_for(FakeFeedback(None)).post("/api/v1/feedback", json=SESSION_JSON)

    assert response.status_code == 200
    assert response.json() == {"feedback": None}


@pytest.mark.parametrize("body", ['{"result": "UNKNOWN"}', "{not json", '{"qualityScore": "many"}'])
def test_rejects_an_invalid_body_with_400_like_spring(body):
    response = client_for().post("/api/v1/feedback", content=body, headers={"Content-Type": "application/json"})

    assert response.status_code == 400


def test_returns_the_ingestion_summary():
    files = [FileResult(file_name="a.pdf", status=IngestionStatus.SKIPPED_UNCHANGED, chunk_count=4)]

    response = client_for(files=files).post("/api/v1/rag/ingest")

    assert response.status_code == 200
    assert response.json() == {
        "filesIngested": 0, "filesReingested": 0, "filesSkipped": 1, "filesFailed": 0, "totalChunksIndexed": 4,
        "files": [{"fileName": "a.pdf", "status": "SKIPPED_UNCHANGED", "chunkCount": 4, "errorMessage": None}],
    }


@pytest.mark.parametrize(("status", "cleared"), [
    (IngestionStatus.INGESTED, True), (IngestionStatus.REINGESTED, True), (IngestionStatus.SKIPPED_UNCHANGED, False),
])
def test_clears_the_feedback_cache_only_when_new_material_was_indexed(status, cleared):
    cache = FeedbackCache(60, 10)
    cache.put("k", "viejo")

    client_for(files=[FileResult(file_name="a.pdf", status=status, chunk_count=1)], cache=cache).post(
        "/api/v1/rag/ingest")

    assert (cache.get("k") is None) is cleared


def test_builds_the_real_services_without_contacting_anything(settings):
    services = build_services(settings)

    assert services.feedback.configuration == "groq:openai/gpt-oss-120b,ollama:phi4-mini"
    assert [provider.name for provider in services.feedback.chain.providers] == ["groq", "ollama"]
    assert services.evaluation.grader.model == "openai/gpt-oss-120b"


def test_builds_only_the_configured_providers(settings, monkeypatch):
    monkeypatch.setenv("FEEDBACK_PROVIDERS", "ollama")

    assert provider_configuration(Settings(_env_file=None)) == "ollama:phi4-mini"
