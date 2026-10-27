"""Arma los objetos del servicio a partir de la configuración (raíz de composición)."""
from dataclasses import dataclass

from app.config import Settings
from app.feedback.cache import FeedbackCache
from app.feedback.prompt_builder import FeedbackPromptBuilder
from app.feedback.providers.base import FeedbackProvider
from app.feedback.providers.chain import ProviderChain
from app.feedback.providers.groq import GroqFeedbackProvider, GroqOptions
from app.feedback.providers.ollama import OllamaFeedbackProvider, OllamaOptions
from app.feedback.service import FeedbackService
from app.feedback.text_cleaner import FeedbackTextCleaner
from app.net.bounded_http import BoundedHttpClient, HttpTimeout
from app.rag.context_retriever import RagContextRetriever
from app.rag.database import Database
from app.rag.embeddings import OllamaEmbeddings
from app.rag.ingestion_service import RagIngestionService
from app.rag.pdf_reader import PdfPageReader
from app.rag.query_builder import RagQueryBuilder
from app.rag.schema import RagSchema
from app.rag.text_splitter import CharacterTextSplitter
from app.roast_reference import DevelopmentTimeRange

MILLIS_PER_SECOND = 1000


@dataclass(frozen=True)
class Services:
    feedback: FeedbackService
    ingestion: RagIngestionService
    feedback_cache: FeedbackCache
    schema: RagSchema


def seconds(millis: int) -> float:
    return millis / MILLIS_PER_SECOND


def build_providers(settings: Settings, http: BoundedHttpClient) -> dict[str, FeedbackProvider]:
    return {
        "groq": GroqFeedbackProvider(http, GroqOptions(
            base_url=settings.groq_base_url, api_key=settings.groq_api_key, model=settings.groq_model,
            temperature=settings.groq_temperature, max_completion_tokens=settings.groq_max_completion_tokens,
            reasoning_effort=settings.groq_reasoning_effort,
            connect_timeout_seconds=seconds(settings.groq_connect_timeout_millis),
            read_timeout_seconds=seconds(settings.groq_read_timeout_millis))),
        "ollama": OllamaFeedbackProvider(http, OllamaOptions(
            base_url=settings.ollama_base_url, model=settings.ollama_feedback_model,
            keep_alive=settings.ollama_keep_alive, temperature=settings.ollama_temperature,
            num_predict=settings.ollama_num_predict,
            connect_timeout_seconds=seconds(settings.ollama_connect_timeout_millis),
            read_timeout_seconds=seconds(settings.ollama_read_timeout_millis))),
    }


def provider_configuration(settings: Settings) -> str:
    models = {"groq": settings.groq_model, "ollama": settings.ollama_feedback_model}
    return ",".join(f"{name}:{models[name]}" for name in settings.feedback_provider_chain)


def build_services(settings: Settings) -> Services:
    http = BoundedHttpClient([settings.groq_base_url, settings.ollama_base_url], settings.http_max_response_bytes)
    database = Database(settings.db_url, settings.db_username, settings.db_password.get_secret_value(),
                        settings.db_connect_timeout_seconds)
    development_range = DevelopmentTimeRange(settings.rag_dtr_optimal_min, settings.rag_dtr_optimal_max)
    embeddings = OllamaEmbeddings(http, settings.ollama_base_url, settings.rag_embedding_model,
                                  settings.rag_embedding_dimensions,
                                  HttpTimeout(seconds(settings.ollama_connect_timeout_millis),
                                              seconds(settings.ollama_read_timeout_millis)))
    providers = build_providers(settings, http)
    cache = FeedbackCache(settings.feedback_cache_ttl_seconds, settings.feedback_cache_max_entries)
    feedback = FeedbackService(
        RagContextRetriever(database, settings.rag_vector_table, embeddings, RagQueryBuilder(development_range),
                            settings.rag_top_k),
        FeedbackPromptBuilder(development_range),
        ProviderChain([providers[name] for name in settings.feedback_provider_chain], FeedbackTextCleaner(),
                      seconds(settings.feedback_total_timeout_millis)),
        cache,
        provider_configuration(settings),
    )
    ingestion = RagIngestionService(
        database, settings.rag_vector_table, settings.rag_docs_dir, PdfPageReader(),
        CharacterTextSplitter(settings.rag_chunk_size_chars, settings.rag_chunk_overlap_chars), embeddings,
        settings.rag_embedding_batch_size,
    )
    schema = RagSchema(database, settings.rag_vector_table, settings.rag_embedding_dimensions)
    return Services(feedback, ingestion, cache, schema)
