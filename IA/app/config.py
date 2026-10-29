from functools import lru_cache
from typing import Literal
from pathlib import Path

from pydantic import Field, SecretStr, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

SERVICE_ROOT = Path(__file__).resolve().parents[1]
SUPPORTED_FEEDBACK_PROVIDERS = frozenset({"groq", "ollama"})
SUPPORTED_TRANSCRIPTION_PROVIDERS = frozenset({"groq", "azure"})
SUPPORTED_SPEECH_PROVIDERS = frozenset({"azure", "piper"})


class Settings(BaseSettings):
    """Configuración del servicio. Cada campo se lee de la variable de entorno con su mismo nombre."""

    model_config = SettingsConfigDict(
        env_file=SERVICE_ROOT / ".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    ai_server_address: str = "127.0.0.1"
    ai_server_port: int = Field(default=8082, ge=1, le=65535)

    db_url: str = "jdbc:postgresql://localhost:5433/toasted_vr_db"
    db_username: str = "toasted_vr_user"
    db_password: SecretStr = SecretStr("")
    db_connect_timeout_seconds: int = Field(default=5, gt=0)

    ollama_base_url: str = "http://localhost:11434"
    ollama_feedback_model: str = "phi4-mini"
    ollama_connect_timeout_millis: int = Field(default=5_000, gt=0)
    ollama_read_timeout_millis: int = Field(default=120_000, gt=0)
    ollama_keep_alive: str = "10m"
    ollama_temperature: float = Field(default=0.4, ge=0)
    ollama_num_predict: int = Field(default=240, gt=0)

    groq_api_key: SecretStr | None = None
    groq_base_url: str = "https://api.groq.com/openai/v1"
    groq_model: str = "openai/gpt-oss-120b"
    groq_connect_timeout_millis: int = Field(default=5_000, gt=0)
    groq_read_timeout_millis: int = Field(default=15_000, gt=0)
    groq_temperature: float = Field(default=0.4, ge=0)
    # gpt-oss razona antes de responder y ese razonamiento también consume este límite.
    groq_max_completion_tokens: int = Field(default=1_024, gt=0)
    groq_reasoning_effort: str = "low"

    # Orden de la cadena de respaldo: se prueba el primero y, si falla, el siguiente.
    feedback_providers: str = "groq,ollama"
    # Tiempo total de la cadena; debe ser menor que AI_SERVICE_FEEDBACK_READ_TIMEOUT_MILLIS del backend.
    feedback_total_timeout_millis: int = Field(default=125_000, gt=0)
    feedback_cache_ttl_seconds: int = Field(default=86_400, ge=0)
    feedback_cache_max_entries: int = Field(default=500, ge=0)

    rag_embedding_model: str = "nomic-embed-text"
    rag_embedding_dimensions: int = Field(default=768, gt=0)
    rag_embedding_batch_size: int = Field(default=32, gt=0)
    rag_vector_table: str = "rag_document_chunks"
    rag_docs_dir: Path = SERVICE_ROOT / "rag-docs"
    # Título legible de cada PDF para las citas: {"archivo.pdf": "Título"}.
    rag_document_titles_file: Path = SERVICE_ROOT / "rag-docs" / "document_titles.json"
    rag_top_k: int = Field(default=4, gt=0)
    rag_chunk_size_chars: int = Field(default=700, gt=0)
    rag_chunk_overlap_chars: int = Field(default=100, ge=0)
    # "characters": ventana por caracteres de siempre. "sentences": limpia el PDF y junta frases
    # completas. Cambiarlo exige reindexar.
    rag_text_splitter: Literal["characters", "sentences"] = "characters"
    rag_min_chunk_chars: int = Field(default=80, ge=0)
    # Una línea que aparece en esta fracción de las páginas de un PDF se trata como encabezado.
    rag_repeated_line_page_ratio: float = Field(default=0.5, gt=0, le=1)
    # Filtros sobre lo recuperado (medidos en IA/eval/results/06). 0 o false los apaga.
    rag_deduplicate_chunks: bool = True
    rag_max_chunks_per_page: int = Field(default=1, ge=0)
    # Candidatos que se piden antes de filtrar, para que queden top_k después de los filtros.
    rag_candidate_pool: int = Field(default=20, gt=0)
    # Development Time Ratio óptimo para cafés de altura de Nariño. Solo describe la
    # desviación en la búsqueda del RAG; no afecta el puntaje ni la fase del tueste.
    rag_dtr_optimal_min: float = 0.18
    rag_dtr_optimal_max: float = 0.22

    http_max_response_bytes: int = Field(default=1_048_576, gt=0)

    # Asistente de voz. El audio de entrada es WAV PCM16 mono a esta frecuencia.
    voice_input_sample_rate_hz: int = Field(default=16_000, gt=0)
    voice_min_audio_millis: int = Field(default=300, gt=0)
    voice_max_audio_seconds: int = Field(default=15, gt=0)
    voice_max_audio_bytes: int = Field(default=600_000, gt=0)
    voice_max_output_audio_bytes: int = Field(default=3_000_000, gt=0)
    voice_max_concurrent_per_user: int = Field(default=1, gt=0)
    voice_max_questions_per_minute: int = Field(default=6, gt=0)
    # Debe ser menor que AI_SERVICE_VOICE_READ_TIMEOUT_MILLIS del backend.
    voice_total_timeout_millis: int = Field(default=25_000, gt=0)
    voice_messages_file: Path = SERVICE_ROOT / "config" / "voice_messages.json"
    voice_silence_phrases_file: Path = SERVICE_ROOT / "config" / "whisper_silence_phrases.json"
    voice_audio_cache_dir: Path = SERVICE_ROOT / "voice-cache"

    voice_transcription_providers: str = "groq,azure"
    groq_transcription_model: str = "whisper-large-v3-turbo"
    groq_transcription_language: str = "es"
    # Vocabulario para que Whisper escriba bien los términos del tueste (máximo 224 tokens).
    groq_transcription_prompt: str = (
        "Preguntas sobre el tueste de café: primer crack, first crack, temperatura de carga, "
        "tiempo de desarrollo, reacción de Maillard, secado, tambor, gas.")
    groq_transcription_read_timeout_millis: int = Field(default=8_000, gt=0)
    voice_max_transcript_chars: int = Field(default=500, gt=0)

    # Groq redacta la respuesta hablada; sin respaldo local para no esperar a phi4-mini.
    voice_groq_model: str = "openai/gpt-oss-20b"
    voice_groq_temperature: float = Field(default=0.2, ge=0)
    voice_groq_max_completion_tokens: int = Field(default=700, gt=0)
    voice_groq_reasoning_effort: str = "low"
    voice_groq_read_timeout_millis: int = Field(default=10_000, gt=0)
    voice_rag_top_k: int = Field(default=3, gt=0)
    voice_history_turns: int = Field(default=2, ge=0)
    voice_answer_max_sentences: int = Field(default=3, gt=0)
    voice_answer_max_chars: int = Field(default=450, gt=0)
    # Diferencia máxima entre una cifra de la respuesta y la de la evidencia (redondeos como 198.4 → 198).
    voice_figure_tolerance: float = Field(default=0.5, ge=0)

    azure_speech_key: SecretStr | None = None
    azure_speech_region: str = Field(default="canadacentral", pattern=r"^[a-z0-9]+$")
    azure_stt_url_template: str = (
        "https://{region}.stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v1")
    azure_stt_language: str = "es-CO"
    azure_tts_url_template: str = "https://{region}.tts.speech.microsoft.com/cognitiveservices/v1"
    azure_tts_voice: str = "es-CO-SalomeNeural"
    azure_tts_language: str = "es-CO"
    azure_tts_output_format: str = "riff-22050hz-16bit-mono-pcm"
    azure_connect_timeout_millis: int = Field(default=3_000, gt=0)
    azure_read_timeout_millis: int = Field(default=8_000, gt=0)
    # La capa F0 trae 500 000 caracteres al mes; al llegar a este límite se usa Piper.
    azure_tts_monthly_char_limit: int = Field(default=450_000, ge=0)

    voice_speech_providers: str = "azure,piper"
    # Rutas relativas a IA/; voice-runtime/ no se sube a git (ver README).
    piper_executable: Path | None = Path("voice-runtime/piper/piper.exe")
    piper_model: Path | None = Path("voice-runtime/voices/es_MX-claude-high.onnx")
    piper_length_scale: float = Field(default=1.0, gt=0)
    piper_timeout_millis: int = Field(default=8_000, gt=0)

    # Reglas, frases por nivel y política del canal de voz de las alertas proactivas.
    alerts_catalog_file: Path = SERVICE_ROOT / "config" / "proactive_alerts.json"
    # Tiempo para sintetizar las frases que falten al pedir un paquete; debe ser menor que
    # AI_SERVICE_ALERTS_READ_TIMEOUT_MILLIS del backend. Lo que no alcance va con audio null.
    alerts_package_timeout_millis: int = Field(default=20_000, gt=0)

    # Calificación de las decisiones del jugador con la rúbrica. Corre en segundo plano desde el backend,
    # por eso espera más que la retroalimentación. Sin respaldo: otro modelo daría otra nota.
    evaluation_rubric_file: Path = SERVICE_ROOT / "config" / "roast_rubric.json"
    evaluation_groq_model: str = "openai/gpt-oss-120b"
    evaluation_groq_temperature: float = Field(default=0, ge=0)
    evaluation_groq_seed: int = 1
    evaluation_groq_max_completion_tokens: int = Field(default=2_048, gt=0)
    evaluation_groq_reasoning_effort: str = "low"
    evaluation_groq_read_timeout_millis: int = Field(default=30_000, gt=0)

    @field_validator("feedback_providers")
    @classmethod
    def known_providers(cls, value: str) -> str:
        return provider_chain(value, SUPPORTED_FEEDBACK_PROVIDERS)

    @field_validator("voice_transcription_providers")
    @classmethod
    def known_transcription_providers(cls, value: str) -> str:
        return provider_chain(value, SUPPORTED_TRANSCRIPTION_PROVIDERS)

    @field_validator("voice_speech_providers")
    @classmethod
    def known_speech_providers(cls, value: str) -> str:
        return provider_chain(value, SUPPORTED_SPEECH_PROVIDERS)

    @property
    def feedback_provider_chain(self) -> list[str]:
        return self.feedback_providers.split(",")

    @property
    def transcription_provider_chain(self) -> list[str]:
        return self.voice_transcription_providers.split(",")

    @property
    def speech_provider_chain(self) -> list[str]:
        return self.voice_speech_providers.split(",")

    @property
    def azure_stt_url(self) -> str:
        return self.azure_stt_url_template.format(region=self.azure_speech_region)

    @property
    def azure_tts_url(self) -> str:
        return self.azure_tts_url_template.format(region=self.azure_speech_region)


def provider_chain(value: str, supported: frozenset[str]) -> str:
    """Normaliza una lista de proveedores separada por comas: sin vacíos, desconocidos ni repetidos."""
    names = [name.strip().lower() for name in value.split(",") if name.strip()]
    unknown = [name for name in names if name not in supported]
    if not names or unknown or len(set(names)) != len(names):
        raise ValueError(f"Invalid provider chain: {value!r}")
    return ",".join(names)


@lru_cache
def get_settings() -> Settings:
    return Settings()
