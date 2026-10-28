"""Contrato JSON de la pregunta por voz entre el backend y el servicio IA (ver docs/unity-voice-contract.md)."""
from enum import StrEnum
from uuid import UUID

from pydantic import ConfigDict, Field

from app.contracts import CamelCaseModel
from app.domain import KnowledgeLevel


class RoastState(CamelCaseModel):
    """Estado del tueste en el momento de la pregunta, tal como lo ve Unity."""

    model_config = ConfigDict(frozen=True, allow_inf_nan=False)

    elapsed_seconds: float = Field(ge=0)
    bean_temperature: float
    first_crack_reached: bool
    target_temperature: float | None = None
    charge_temperature: float | None = None
    first_crack_elapsed_seconds: float | None = Field(default=None, ge=0)
    # Opcionales: si Unity no los manda, el asistente responde igual sin ellos.
    heat_level_percent: float | None = Field(default=None, ge=0, le=100)
    temperature_rise_per_minute: float | None = None


class VoiceQuestionContext(CamelCaseModel):
    """Lo que acompaña al audio. userId y knowledgeLevel los pone el backend, nunca Unity."""

    model_config = ConfigDict(frozen=True)

    user_id: int = Field(gt=0)
    knowledge_level: KnowledgeLevel
    roast_attempt_id: UUID
    roast_state: RoastState


class AnswerStatus(StrEnum):
    ANSWERED = "ANSWERED"
    # No hay evidencia en las fuentes ni en el estado del tueste: se dice que no se sabe.
    ABSTAINED = "ABSTAINED"
    # Groq u otro paso necesario falló: se responde la frase fija con su audio guardado.
    UNAVAILABLE = "UNAVAILABLE"


class CitationResponse(CamelCaseModel):
    file_name: str
    page_number: int
    title: str


class AudioResponse(CamelCaseModel):
    format: str
    sample_rate_hz: int
    base64: str


class ProvidersResponse(CamelCaseModel):
    transcription: str | None
    answer: str | None
    speech: str | None


class LatencyResponse(CamelCaseModel):
    transcription_millis: int
    retrieval_millis: int
    answer_millis: int
    speech_millis: int
    total_millis: int


class VoiceAnswerResponse(CamelCaseModel):
    status: AnswerStatus
    question: str | None
    answer: str
    citations: list[CitationResponse]
    audio: AudioResponse | None
    providers: ProvidersResponse
    latency: LatencyResponse


class VoiceErrorResponse(CamelCaseModel):
    code: str
    message: str
