import json

from app.contracts import (
    FeedbackResponse,
    FileResult,
    IngestionStatus,
    RagIngestionSummaryResponse,
    RoastFeedbackRequest,
)
from app.domain import KnowledgeLevel, RoastingResult


def test_reads_the_session_sent_by_the_backend():
    payload = """
        {
          "result": "PERFECT",
          "qualityScore": 92,
          "chargeTemperature": 190.0,
          "targetTemperature": 200.0,
          "finalTemperature": 201.5,
          "totalDurationSeconds": 780,
          "firstCrackReached": true,
          "developmentTimeSeconds": 156,
          "knowledgeLevel": "BEGINNER"
        }
    """

    request = RoastFeedbackRequest.model_validate_json(payload)

    assert request.result is RoastingResult.PERFECT
    assert request.quality_score == 92
    assert request.charge_temperature == 190.0
    assert request.target_temperature == 200.0
    assert request.final_temperature == 201.5
    assert request.total_duration_seconds == 780
    assert request.first_crack_reached is True
    assert request.development_time_seconds == 156
    assert request.knowledge_level is KnowledgeLevel.BEGINNER


def test_accepts_missing_optional_values():
    payload = """
        {"result": "RAW", "qualityScore": 20, "targetTemperature": 210.0,
         "finalTemperature": 180.0, "totalDurationSeconds": 500, "firstCrackReached": false}
    """

    request = RoastFeedbackRequest.model_validate_json(payload)

    assert request.charge_temperature is None
    assert request.development_time_seconds is None
    assert request.knowledge_level is None


def test_reads_integer_temperatures_as_decimals():
    request = RoastFeedbackRequest.model_validate({"targetTemperature": 200})

    assert isinstance(request.target_temperature, float)


def test_ignores_unknown_fields_like_jackson():
    request = RoastFeedbackRequest.model_validate({"result": "BAKED", "extra": 1})

    assert request.result is RoastingResult.BAKED


def test_writes_null_feedback_explicitly():
    assert FeedbackResponse(feedback=None).model_dump(by_alias=True) == {"feedback": None}


def test_writes_the_ingestion_summary_with_the_java_field_names_and_order():
    summary = RagIngestionSummaryResponse.from_files([
        FileResult(file_name="a.pdf", status=IngestionStatus.INGESTED, chunk_count=3),
        FileResult(file_name="b.pdf", status=IngestionStatus.REINGESTED, chunk_count=2),
        FileResult(file_name="c.pdf", status=IngestionStatus.SKIPPED_UNCHANGED, chunk_count=5),
        FileResult(file_name="d.pdf", status=IngestionStatus.READ_ERROR, chunk_count=0),
        FileResult(file_name="e.pdf", status=IngestionStatus.FAILED, chunk_count=0, error_message="boom"),
    ])

    written = json.loads(summary.model_dump_json(by_alias=True))

    assert list(written) == [
        "filesIngested", "filesReingested", "filesSkipped", "filesFailed", "totalChunksIndexed", "files",
    ]
    assert [written[key] for key in list(written)[:5]] == [1, 1, 1, 2, 10]
    assert written["files"][0] == {
        "fileName": "a.pdf", "status": "INGESTED", "chunkCount": 3, "errorMessage": None,
    }
    assert written["files"][4]["errorMessage"] == "boom"


def test_summarizes_an_empty_ingestion():
    summary = RagIngestionSummaryResponse.from_files([])

    assert summary.files_failed == 0
    assert summary.total_chunks_indexed == 0
    assert summary.files == []
