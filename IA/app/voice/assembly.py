"""Arma el asistente de voz a partir de la configuración. Lo llama app/container.py."""
from pathlib import Path

from app.config import SERVICE_ROOT, Settings
from app.net.bounded_http import BoundedHttpClient
from app.rag.chunk_search import ChunkSearch
from app.rag.citations import CitationResolver
from app.rag.database import Database
from app.voice.answer.groq import GroqAnswerOptions, GroqVoiceAnswerer
from app.voice.answer.figures import SupportedFigures
from app.voice.answer.grounding import AnswerGrounding, SpokenTextLimiter
from app.voice.answer.guide import load_roast_guide
from app.voice.answer.prompt import VoiceAnswerPromptBuilder
from app.voice.answer.retriever import QuestionPassageRetriever
from app.voice.audio import WaveLimits, WaveValidator
from app.voice.history import VoiceExchangeRepository
from app.voice.limits import UserRequestLimiter
from app.voice.messages import VoiceMessages
from app.voice.service import VoiceAssistantService, VoicePipeline
from app.voice.silence import SilencePhraseFilter
from app.voice.speech.azure import AzureSpeechOptions, AzureSynthesizer
from app.voice.speech.base import Synthesizer
from app.voice.speech.chain import SpeechChain
from app.voice.speech.phrase_cache import PhraseAudioCache
from app.voice.speech.piper import PiperOptions, PiperSynthesizer
from app.voice.speech.quota import MonthlyCharacterQuota
from app.voice.transcription.azure import AzureTranscriber, AzureTranscriptionOptions
from app.voice.transcription.base import Transcriber
from app.voice.transcription.chain import TranscriptionChain
from app.voice.transcription.groq import GroqTranscriber, GroqTranscriptionOptions

MILLIS_PER_SECOND = 1000
AZURE_QUOTA_PROVIDER = "azure"


def seconds(millis: int) -> float:
    return millis / MILLIS_PER_SECOND


def voice_base_urls(settings: Settings) -> list[str]:
    """Orígenes que el cliente HTTP acotado debe permitir para la voz."""
    return [settings.azure_stt_url, settings.azure_tts_url]


def service_path(path: Path | None) -> Path | None:
    """Las rutas relativas de la configuración (Piper) se toman desde la carpeta IA/."""
    if path is None:
        return None
    return path if path.is_absolute() else SERVICE_ROOT / path


def build_transcribers(settings: Settings, http: BoundedHttpClient) -> dict[str, Transcriber]:
    return {
        "groq": GroqTranscriber(http, GroqTranscriptionOptions(
            base_url=settings.groq_base_url, api_key=settings.groq_api_key, model=settings.groq_transcription_model,
            language=settings.groq_transcription_language, prompt=settings.groq_transcription_prompt,
            connect_timeout_seconds=seconds(settings.groq_connect_timeout_millis),
            read_timeout_seconds=seconds(settings.groq_transcription_read_timeout_millis))),
        "azure": AzureTranscriber(http, AzureTranscriptionOptions(
            url=settings.azure_stt_url, api_key=settings.azure_speech_key, language=settings.azure_stt_language,
            sample_rate_hz=settings.voice_input_sample_rate_hz,
            connect_timeout_seconds=seconds(settings.azure_connect_timeout_millis),
            read_timeout_seconds=seconds(settings.azure_read_timeout_millis),
            max_response_bytes=settings.http_max_response_bytes)),
    }


def build_synthesizers(settings: Settings, http: BoundedHttpClient) -> dict[str, Synthesizer]:
    return {
        "azure": AzureSynthesizer(http, AzureSpeechOptions(
            url=settings.azure_tts_url, api_key=settings.azure_speech_key, voice=settings.azure_tts_voice,
            language=settings.azure_tts_language, output_format=settings.azure_tts_output_format,
            connect_timeout_seconds=seconds(settings.azure_connect_timeout_millis),
            read_timeout_seconds=seconds(settings.azure_read_timeout_millis),
            max_audio_bytes=settings.voice_max_output_audio_bytes)),
        "piper": PiperSynthesizer(PiperOptions(
            executable=service_path(settings.piper_executable), model=service_path(settings.piper_model),
            length_scale=settings.piper_length_scale, timeout_seconds=seconds(settings.piper_timeout_millis),
            max_audio_bytes=settings.voice_max_output_audio_bytes)),
    }


def voice_signature(settings: Settings) -> str:
    """Identifica la voz configurada: si cambia, el audio guardado de las frases fijas se regenera."""
    piper_model = service_path(settings.piper_model)
    return ":".join([settings.voice_speech_providers, settings.azure_tts_voice, settings.azure_tts_output_format,
                     piper_model.name if piper_model else "", f"{settings.piper_length_scale:.2f}"])


def build_speech(settings: Settings, http: BoundedHttpClient, database: Database) -> SpeechChain:
    synthesizers = build_synthesizers(settings, http)
    quota = MonthlyCharacterQuota(database, AZURE_QUOTA_PROVIDER, settings.azure_tts_monthly_char_limit)
    return SpeechChain([synthesizers[name] for name in settings.speech_provider_chain], {AZURE_QUOTA_PROVIDER: quota})


def build_voice(settings: Settings, http: BoundedHttpClient, database: Database, chunk_search: ChunkSearch,
                citations: CitationResolver) -> VoiceAssistantService:
    messages = VoiceMessages.from_file(settings.voice_messages_file)
    transcribers = build_transcribers(settings, http)
    speech = build_speech(settings, http, database)
    pipeline = VoicePipeline(
        validator=WaveValidator(WaveLimits(settings.voice_input_sample_rate_hz, settings.voice_min_audio_millis,
                                           settings.voice_max_audio_seconds, settings.voice_max_audio_bytes)),
        limiter=UserRequestLimiter(settings.voice_max_concurrent_per_user, settings.voice_max_questions_per_minute),
        transcription=TranscriptionChain([transcribers[name] for name in settings.transcription_provider_chain],
                                         SilencePhraseFilter.from_file(settings.voice_silence_phrases_file),
                                         settings.voice_max_transcript_chars),
        retriever=QuestionPassageRetriever(chunk_search, citations, settings.voice_rag_top_k),
        prompt_builder=VoiceAnswerPromptBuilder(settings.voice_answer_max_sentences,
                                                load_roast_guide(settings.voice_roast_guide_file)),
        answerer=GroqVoiceAnswerer(http, GroqAnswerOptions(
            base_url=settings.groq_base_url, api_key=settings.groq_api_key, model=settings.voice_groq_model,
            temperature=settings.voice_groq_temperature,
            max_completion_tokens=settings.voice_groq_max_completion_tokens,
            reasoning_effort=settings.voice_groq_reasoning_effort,
            connect_timeout_seconds=seconds(settings.groq_connect_timeout_millis),
            read_timeout_seconds=seconds(settings.voice_groq_read_timeout_millis))),
        grounding=AnswerGrounding(messages.abstention, SpokenTextLimiter(settings.voice_answer_max_sentences,
                                                                         settings.voice_answer_max_chars),
                                  SupportedFigures(settings.voice_figure_tolerance)),
        speech=speech,
        phrases=PhraseAudioCache(speech, settings.voice_audio_cache_dir, voice_signature(settings)),
        history=VoiceExchangeRepository(database),
    )
    return VoiceAssistantService(pipeline, messages, settings.voice_history_turns,
                                 seconds(settings.voice_total_timeout_millis))
