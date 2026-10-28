from functools import lru_cache
from pathlib import Path

from pydantic import Field, SecretStr, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

SERVICE_ROOT = Path(__file__).resolve().parents[1]
SUPPORTED_FEEDBACK_PROVIDERS = frozenset({"groq", "ollama"})


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
    # Prefijo de tarea para las búsquedas (nomic-embed-text: "search_query: "). Vacío = sin prefijo.
    rag_query_prefix: str = ""
    # Traduce la búsqueda con Groq para encontrar también los PDFs en ese idioma.
    rag_query_translation_enabled: bool = False
    rag_query_translation_language: str = "English"
    rag_vector_table: str = "rag_document_chunks"
    rag_docs_dir: Path = SERVICE_ROOT / "rag-docs"
    rag_top_k: int = Field(default=4, gt=0)
    rag_chunk_size_chars: int = Field(default=700, gt=0)
    rag_chunk_overlap_chars: int = Field(default=100, ge=0)
    # Filtros sobre lo recuperado. 0 (o false) los deja apagados.
    rag_min_similarity: float = Field(default=0.0, ge=0, le=1)
    rag_deduplicate_chunks: bool = False
    rag_max_chunks_per_page: int = Field(default=0, ge=0)
    # Candidatos que se piden antes de filtrar, para que queden top_k después de los filtros.
    rag_candidate_pool: int = Field(default=20, gt=0)
    # Development Time Ratio óptimo para cafés de altura de Nariño. Solo describe la
    # desviación en la búsqueda del RAG; no afecta el puntaje ni la fase del tueste.
    rag_dtr_optimal_min: float = 0.18
    rag_dtr_optimal_max: float = 0.22

    http_max_response_bytes: int = Field(default=1_048_576, gt=0)

    @field_validator("feedback_providers")
    @classmethod
    def known_providers(cls, value: str) -> str:
        names = [name.strip().lower() for name in value.split(",") if name.strip()]
        unknown = [name for name in names if name not in SUPPORTED_FEEDBACK_PROVIDERS]
        if not names or unknown or len(set(names)) != len(names):
            raise ValueError(f"Invalid feedback provider chain: {value!r}")
        return ",".join(names)

    @property
    def feedback_provider_chain(self) -> list[str]:
        return self.feedback_providers.split(",")


@lru_cache
def get_settings() -> Settings:
    return Settings()
