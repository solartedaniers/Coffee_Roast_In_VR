import pytest
from pydantic import ValidationError

from app.config import SERVICE_ROOT, Settings


def test_listens_only_on_loopback_by_default(settings):
    assert settings.ai_server_address == "127.0.0.1"
    assert settings.ai_server_port == 8082


def test_keeps_the_ollama_generation_defaults(settings):
    assert settings.ollama_feedback_model == "phi4-mini"
    assert settings.ollama_connect_timeout_millis == 5000
    assert settings.ollama_read_timeout_millis == 120000
    assert settings.ollama_keep_alive == "10m"
    assert settings.ollama_temperature == 0.4
    assert settings.ollama_num_predict == 240


def test_keeps_the_rag_defaults(settings):
    assert settings.rag_top_k == 4
    assert settings.rag_chunk_size_chars == 700
    assert settings.rag_chunk_overlap_chars == 100
    assert settings.rag_dtr_optimal_min == 0.18
    assert settings.rag_dtr_optimal_max == 0.22
    assert settings.rag_docs_dir == SERVICE_ROOT / "rag-docs"


def test_removes_repeated_chunks_and_takes_one_per_page_by_default(settings):
    assert settings.rag_deduplicate_chunks is True
    assert settings.rag_max_chunks_per_page == 1
    assert settings.rag_text_splitter == "characters"


def test_keeps_the_same_pgvector_table(settings):
    assert settings.rag_vector_table == "rag_document_chunks"
    assert settings.rag_embedding_model == "nomic-embed-text"
    assert settings.rag_embedding_dimensions == 768


def test_uses_groq_first_and_ollama_as_fallback(settings):
    assert settings.feedback_provider_chain == ["groq", "ollama"]
    assert settings.groq_model == "openai/gpt-oss-120b"
    assert settings.groq_api_key is None


def test_ends_the_chain_before_the_backend_stops_waiting(settings):
    backend_feedback_read_timeout_millis = 130_000
    assert settings.feedback_total_timeout_millis < backend_feedback_read_timeout_millis


def test_caches_feedback_for_a_day_with_500_entries(settings):
    assert settings.feedback_cache_ttl_seconds == 86400
    assert settings.feedback_cache_max_entries == 500


def test_reads_values_from_the_environment(settings, monkeypatch):
    monkeypatch.setenv("GROQ_MODEL", "openai/gpt-oss-20b")
    monkeypatch.setenv("FEEDBACK_PROVIDERS", " Ollama ")
    monkeypatch.setenv("GROQ_API_KEY", "test-key")

    configured = Settings(_env_file=None)

    assert configured.groq_model == "openai/gpt-oss-20b"
    assert configured.feedback_provider_chain == ["ollama"]
    assert configured.groq_api_key.get_secret_value() == "test-key"
    assert "test-key" not in repr(configured)


@pytest.mark.parametrize("chain", ["", "openai", "groq,groq"])
def test_rejects_an_invalid_provider_chain(settings, monkeypatch, chain):
    monkeypatch.setenv("FEEDBACK_PROVIDERS", chain)

    with pytest.raises(ValidationError):
        Settings(_env_file=None)


def test_keeps_the_voice_assistant_defaults(settings):
    assert settings.voice_max_audio_seconds == 15
    assert settings.voice_max_audio_bytes == 600_000
    assert settings.voice_max_concurrent_per_user == 1
    assert settings.voice_max_questions_per_minute == 6
    assert settings.transcription_provider_chain == ["groq", "azure"]
    assert settings.speech_provider_chain == ["azure", "piper"]
    assert settings.voice_groq_model == "openai/gpt-oss-20b"
    assert settings.azure_tts_voice == "es-CO-SalomeNeural"
    assert settings.azure_tts_monthly_char_limit == 450_000
    assert settings.azure_speech_key is None


def test_builds_the_azure_urls_from_the_region(settings, monkeypatch):
    monkeypatch.setenv("AZURE_SPEECH_REGION", "eastus")

    configured = Settings(_env_file=None)

    assert configured.azure_tts_url == "https://eastus.tts.speech.microsoft.com/cognitiveservices/v1"
    assert configured.azure_stt_url.startswith("https://eastus.stt.speech.microsoft.com/")


def test_ends_the_voice_question_before_the_backend_stops_waiting(settings):
    backend_voice_read_timeout_millis = 30_000
    assert settings.voice_total_timeout_millis < backend_voice_read_timeout_millis


@pytest.mark.parametrize(("variable", "chain"), [
    ("VOICE_TRANSCRIPTION_PROVIDERS", "whisper"), ("VOICE_SPEECH_PROVIDERS", "azure,azure"),
    ("VOICE_SPEECH_PROVIDERS", ""), ("AZURE_SPEECH_REGION", "canada central"),
])
def test_rejects_an_invalid_voice_configuration(settings, monkeypatch, variable, chain):
    monkeypatch.setenv(variable, chain)

    with pytest.raises(ValidationError):
        Settings(_env_file=None)
