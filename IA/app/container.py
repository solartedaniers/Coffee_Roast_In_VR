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
from app.rag.chunk_filters import (
    ChunkFilter,
    DuplicateTextFilter,
    FilteredChunkSearch,
    MinimumSimilarityFilter,
    PageQuotaFilter,
)
from app.rag.chunk_search import ChunkSearch, SemanticChunkSearch
from app.rag.citations import CitationResolver, load_document_titles
from app.rag.context_retriever import RagContextRetriever
from app.rag.database import Database
from app.rag.embeddings import OllamaEmbeddings
from app.rag.ingestion_service import RagIngestionService
from app.rag.pdf_catalog import PdfCatalog
from app.rag.pdf_reader import PdfPageReader
from app.rag.query_builder import RagQueryBuilder
from app.rag.schema import RagSchema
from app.rag.pdf_text_cleaner import PdfTextCleaner
from app.rag.sentence_splitter import CleaningTextSplitter, SentenceTextSplitter
from app.rag.text_splitter import CharacterTextSplitter, TextSplitter
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


def build_http(settings: Settings) -> BoundedHttpClient:
    return BoundedHttpClient([settings.groq_base_url, settings.ollama_base_url], settings.http_max_response_bytes)


def build_database(settings: Settings, read_only: bool = False) -> Database:
    return Database(settings.db_url, settings.db_username, settings.db_password.get_secret_value(),
                    settings.db_connect_timeout_seconds, read_only)


def build_embeddings(settings: Settings, http: BoundedHttpClient) -> OllamaEmbeddings:
    return OllamaEmbeddings(http, settings.ollama_base_url, settings.rag_embedding_model,
                            settings.rag_embedding_dimensions,
                            HttpTimeout(seconds(settings.ollama_connect_timeout_millis),
                                        seconds(settings.ollama_read_timeout_millis)))


def build_citations(settings: Settings) -> CitationResolver:
    return CitationResolver(PdfCatalog(settings.rag_docs_dir), load_document_titles(settings.rag_document_titles_file))


def build_text_splitter(settings: Settings) -> TextSplitter:
    if settings.rag_text_splitter == "sentences":
        return CleaningTextSplitter(
            PdfTextCleaner(settings.rag_repeated_line_page_ratio),
            SentenceTextSplitter(settings.rag_chunk_size_chars, settings.rag_chunk_overlap_chars,
                                 settings.rag_min_chunk_chars))
    return CharacterTextSplitter(settings.rag_chunk_size_chars, settings.rag_chunk_overlap_chars)


def build_chunk_search(settings: Settings, database: Database, embeddings: OllamaEmbeddings) -> ChunkSearch:
    """La misma búsqueda la usan la retroalimentación y la evaluación del RAG."""
    search = SemanticChunkSearch(database, settings.rag_vector_table, embeddings, settings.rag_query_prefix)
    filters = build_chunk_filters(settings)
    return FilteredChunkSearch(search, filters, settings.rag_candidate_pool) if filters else search


def build_chunk_filters(settings: Settings) -> list[ChunkFilter]:
    filters: list[ChunkFilter] = []
    if settings.rag_min_similarity > 0:
        filters.append(MinimumSimilarityFilter(settings.rag_min_similarity))
    if settings.rag_deduplicate_chunks:
        filters.append(DuplicateTextFilter())
    if settings.rag_max_chunks_per_page > 0:
        filters.append(PageQuotaFilter(settings.rag_max_chunks_per_page))
    return filters


def build_services(settings: Settings) -> Services:
    http = build_http(settings)
    database = build_database(settings)
    development_range = DevelopmentTimeRange(settings.rag_dtr_optimal_min, settings.rag_dtr_optimal_max)
    embeddings = build_embeddings(settings, http)
    providers = build_providers(settings, http)
    cache = FeedbackCache(settings.feedback_cache_ttl_seconds, settings.feedback_cache_max_entries)
    feedback = FeedbackService(
        RagContextRetriever(build_chunk_search(settings, database, embeddings),
                            RagQueryBuilder(development_range), settings.rag_top_k, build_citations(settings)),
        FeedbackPromptBuilder(development_range),
        ProviderChain([providers[name] for name in settings.feedback_provider_chain], FeedbackTextCleaner(),
                      seconds(settings.feedback_total_timeout_millis)),
        cache,
        provider_configuration(settings),
    )
    ingestion = RagIngestionService(
        database, settings.rag_vector_table, settings.rag_docs_dir, PdfPageReader(),
        build_text_splitter(settings), embeddings,
        settings.rag_embedding_batch_size, settings.rag_document_prefix,
    )
    schema = RagSchema(database, settings.rag_vector_table, settings.rag_embedding_dimensions)
    return Services(feedback, ingestion, cache, schema)
