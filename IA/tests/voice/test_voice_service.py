from uuid import UUID

import pytest

from app.config import SERVICE_ROOT
from app.domain import KnowledgeLevel
from app.rag.citations import Citation, RetrievedPassage
from app.voice.answer.groq import AnswerUnavailable, ModelAnswer
from app.voice.answer.figures import SupportedFigures
from app.voice.answer.grounding import AnswerGrounding, SpokenTextLimiter
from app.voice.answer.prompt import ConversationTurn, VoiceAnswerPromptBuilder
from app.voice.audio import WaveLimits, WaveValidator
from app.voice.contracts import AnswerStatus, RoastState, VoiceQuestionContext
from app.voice.errors import VoiceErrorCode, VoiceRequestError
from app.voice.limits import UserRequestLimiter
from app.voice.messages import VoiceMessages
from app.voice.service import VoiceAssistantService, VoicePipeline
from app.voice.silence import SilencePhraseFilter
from app.voice.speech.base import SynthesizedAudio
from app.voice.speech.chain import SpeechChain
from app.voice.speech.phrase_cache import PhraseAudioCache
from app.voice.transcription.base import TranscriptionUnavailable
from app.voice.transcription.chain import TranscriptionChain
from tests.voice.fakes import FakeTranscriber
from tests.voice.wav_factory import tone_wav

MESSAGES = VoiceMessages.from_file(SERVICE_ROOT / "config" / "voice_messages.json")
SILENCE = SilencePhraseFilter.from_file(SERVICE_ROOT / "config" / "whisper_silence_phrases.json")
MANUAL = Citation("manual.pdf", 12, "Manual de tueste")
CONTEXT = VoiceQuestionContext(
    user_id=7, knowledge_level=KnowledgeLevel.BEGINNER, roast_attempt_id=UUID("8a7c6f0e-2a55-4c1e-9d43-0b6f1f3d2e10"),
    roast_state=RoastState(elapsed_seconds=300, bean_temperature=190, first_crack_reached=False))
WAV = tone_wav(1.0)


class FakeRetriever:
    def __init__(self, passages=(RetrievedPassage("El primer crack ocurre cerca de 196 °C.", MANUAL),)):
        self.passages = list(passages)
        self.questions = []

    def find(self, question):
        self.questions.append(question)
        return self.passages


class FakeAnswerer:
    name = "groq"

    def __init__(self, answer=None, error=None):
        self.answer_value = answer or ModelAnswer(sufficient=True, answer="Falta poco, sigue así.",
                                                  source_ids=["S1", "ROAST"])
        self.error = error
        self.prompts = []

    def answer(self, prompt, time_budget_seconds):
        self.prompts.append(prompt)
        if self.error:
            raise self.error
        return self.answer_value


class FakeSynthesizer:
    name = "azure"

    def __init__(self):
        self.texts = []

    def synthesize(self, text, time_budget_seconds):
        self.texts.append(text)
        return SynthesizedAudio(tone_wav(0.2, rate=22_050), 22_050, "azure")


class FakeHistory:
    def __init__(self, turns=(), error=None):
        self.turns = list(turns)
        self.error = error
        self.saved = []

    def recent_answered(self, user_id, roast_attempt_id, limit):
        if self.error:
            raise self.error
        return self.turns[-limit:]

    def save(self, exchange):
        if self.error:
            raise self.error
        self.saved.append(exchange)


def service(tmp_path, transcriber=None, answerer=None, history=None, retriever=None, synthesizer=None):
    synthesizer = synthesizer or FakeSynthesizer()
    speech = SpeechChain([synthesizer], {})
    pipeline = VoicePipeline(
        validator=WaveValidator(WaveLimits(16_000, 300, 15, 600_000)),
        limiter=UserRequestLimiter(1, 6),
        transcription=TranscriptionChain([transcriber or FakeTranscriber("groq", result="¿Cuándo llega el crack?")],
                                         SILENCE, 500),
        retriever=retriever or FakeRetriever(),
        prompt_builder=VoiceAnswerPromptBuilder(3),
        answerer=answerer or FakeAnswerer(),
        grounding=AnswerGrounding(MESSAGES.abstention, SpokenTextLimiter(3, 450), SupportedFigures(0.5)),
        speech=speech,
        phrases=PhraseAudioCache(speech, tmp_path, "test-voice"),
        history=history if history is not None else FakeHistory(),
    )
    return VoiceAssistantService(pipeline, MESSAGES, 2, 25)


def test_answers_a_question_with_sources_roast_state_level_and_audio(tmp_path):
    answerer, history, retriever = FakeAnswerer(), FakeHistory(), FakeRetriever()

    answer = service(tmp_path, answerer=answerer, history=history, retriever=retriever).ask(CONTEXT, WAV)

    assert answer.status is AnswerStatus.ANSWERED
    assert (answer.question, answer.text) == ("¿Cuándo llega el crack?", "Falta poco, sigue así.")
    assert answer.citations == [MANUAL]
    assert answer.audio.provider == "azure"
    assert (answer.transcription_provider, answer.answer_provider) == ("groq", "groq")
    assert set(answer.step_millis) == {"transcription", "retrieval", "answer", "speech"}
    assert retriever.questions == ["¿Cuándo llega el crack?"]
    prompt = answerer.prompts[0]
    assert "190.0 °C" in prompt.user and "principiante" in prompt.system
    assert history.saved[0].status is AnswerStatus.ANSWERED
    assert history.saved[0].roast_attempt_id == CONTEXT.roast_attempt_id


def test_abstains_with_the_fixed_phrase_and_reuses_its_audio(tmp_path):
    synthesizer = FakeSynthesizer()
    abstaining = FakeAnswerer(ModelAnswer(sufficient=False, answer="", source_ids=[]))
    assistant = service(tmp_path, answerer=abstaining, synthesizer=synthesizer)

    first = assistant.ask(CONTEXT, WAV)
    second = assistant.ask(CONTEXT, WAV)

    assert first.status is second.status is AnswerStatus.ABSTAINED
    assert first.text == MESSAGES.abstention and first.citations == []
    assert first.audio is not None
    assert synthesizer.texts == [MESSAGES.abstention]


@pytest.mark.parametrize("failing", ["transcription", "answer"])
def test_answers_the_unavailable_phrase_with_audio_when_a_provider_fails(tmp_path, failing):
    kwargs = ({"transcriber": FakeTranscriber("groq", error=TranscriptionUnavailable("caído"))}
              if failing == "transcription" else {"answerer": FakeAnswerer(error=AnswerUnavailable("429"))})
    history = FakeHistory()

    answer = service(tmp_path, history=history, **kwargs).ask(CONTEXT, WAV)

    assert answer.status is AnswerStatus.UNAVAILABLE
    assert answer.text == MESSAGES.unavailable
    assert answer.audio is not None
    assert history.saved[0].status is AnswerStatus.UNAVAILABLE


def test_uses_the_recent_turns_of_this_attempt(tmp_path):
    answerer = FakeAnswerer()
    history = FakeHistory([ConversationTurn("¿Subo el gas?", "Todavía no.")])

    service(tmp_path, answerer=answerer, history=history).ask(CONTEXT, WAV)

    assert "Jugador: ¿Subo el gas?" in answerer.prompts[0].user


def test_still_answers_when_the_history_table_fails(tmp_path):
    answer = service(tmp_path, history=FakeHistory(error=ConnectionError("db"))).ask(CONTEXT, WAV)

    assert answer.status is AnswerStatus.ANSWERED


def test_rejects_bad_audio_before_using_the_minute_quota(tmp_path):
    assistant = service(tmp_path)

    for _ in range(10):
        with pytest.raises(VoiceRequestError) as error:
            assistant.ask(CONTEXT, b"no es audio")
        assert error.value.code is VoiceErrorCode.AUDIO_INVALID
    assert assistant.ask(CONTEXT, WAV).status is AnswerStatus.ANSWERED


def test_a_silent_take_is_an_error_for_unity_and_is_not_saved(tmp_path):
    history = FakeHistory()

    with pytest.raises(VoiceRequestError) as error:
        service(tmp_path, transcriber=FakeTranscriber("groq", result="Gracias por ver el video."),
                history=history).ask(CONTEXT, WAV)

    assert error.value.code is VoiceErrorCode.SILENT_AUDIO
    assert history.saved == []


def test_warms_the_fixed_phrases(tmp_path):
    synthesizer = FakeSynthesizer()

    service(tmp_path, synthesizer=synthesizer).warm_up(5)

    assert synthesizer.texts == [MESSAGES.unavailable, MESSAGES.abstention]
