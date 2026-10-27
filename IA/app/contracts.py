"""Contrato JSON con el backend. Los nombres y la forma deben seguir iguales a los del servicio en Java."""
from enum import StrEnum

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel

from app.domain import KnowledgeLevel, RoastingResult


class CamelCaseModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class RoastFeedbackRequest(CamelCaseModel):
    """Sesión de tueste ya evaluada que envía el backend. Todos los campos son opcionales, igual que en Java."""

    model_config = ConfigDict(frozen=True)

    result: RoastingResult | None = None
    quality_score: int | None = None
    charge_temperature: float | None = None
    target_temperature: float | None = None
    final_temperature: float | None = None
    total_duration_seconds: int | None = None
    first_crack_reached: bool | None = None
    development_time_seconds: int | None = None
    knowledge_level: KnowledgeLevel | None = None


class FeedbackResponse(CamelCaseModel):
    # None cuando ningún proveedor respondió; el backend lo convierte en "".
    feedback: str | None


class IngestionStatus(StrEnum):
    INGESTED = "INGESTED"
    REINGESTED = "REINGESTED"
    SKIPPED_UNCHANGED = "SKIPPED_UNCHANGED"
    READ_ERROR = "READ_ERROR"
    # Falla después de leer el PDF (partir, embeddings o insertar).
    FAILED = "FAILED"


class FileResult(CamelCaseModel):
    file_name: str
    status: IngestionStatus
    chunk_count: int
    error_message: str | None = None


class RagIngestionSummaryResponse(CamelCaseModel):
    files_ingested: int
    files_reingested: int
    files_skipped: int
    files_failed: int
    total_chunks_indexed: int
    files: list[FileResult]

    @classmethod
    def from_files(cls, files: list[FileResult]) -> "RagIngestionSummaryResponse":
        def count(*statuses: IngestionStatus) -> int:
            return sum(1 for file in files if file.status in statuses)

        return cls(
            files_ingested=count(IngestionStatus.INGESTED),
            files_reingested=count(IngestionStatus.REINGESTED),
            files_skipped=count(IngestionStatus.SKIPPED_UNCHANGED),
            files_failed=count(IngestionStatus.READ_ERROR, IngestionStatus.FAILED),
            total_chunks_indexed=sum(file.chunk_count for file in files),
            files=files,
        )
