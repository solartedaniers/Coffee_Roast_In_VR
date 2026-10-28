"""Coordina la pregunta por voz: audio → texto → fuentes y estado del tueste → respuesta → voz."""
import logging
import time
from dataclasses import dataclass

from app.rag.citations import Citation
from app.voice.answer.groq import AnswerUnavailable, GroqVoiceAnswerer
from app.voice.answer.grounding import AnswerGrounding
from app.voice.answer.prompt import ConversationTurn, VoiceAnswerPromptBuilder
from app.voice.answer.retriever import QuestionPassageRetriever
from app.voice.audio import WaveValidator
from app.voice.contracts import AnswerStatus, VoiceQuestionContext
from app.voice.deadline import Deadline
from app.voice.history import VoiceExchange, VoiceExchangeRepository
from app.voice.limits import UserRequestLimiter
from app.voice.messages import VoiceMessages
from app.voice.speech.base import SynthesizedAudio
from app.voice.speech.chain import SpeechChain
from app.voice.speech.phrase_cache import PhraseAudioCache
from app.voice.transcription.base import TranscriptionUnavailable
from app.voice.transcription.chain import TranscriptionChain

log = logging.getLogger(__name__)
MILLIS_PER_SECOND = 1000


class StepTimes:
    """Milisegundos de cada paso, para medir la latencia real desde la respuesta."""

    def __init__(self, clock):
        self.clock = clock
        self.started_at = clock()
        self.millis: dict[str, int] = {}

    def measure(self, step: str, since: float) -> float:
        now = self.clock()
        self.millis[step] = self.millis.get(step, 0) + round((now - since) * MILLIS_PER_SECOND)
        return now

    def total(self) -> int:
        return round((self.clock() - self.started_at) * MILLIS_PER_SECOND)


@dataclass(frozen=True)
class VoiceAnswer:
    status: AnswerStatus
    question: str | None
    text: str
    citations: list[Citation]
    audio: SynthesizedAudio | None
    transcription_provider: str | None
    answer_provider: str | None
    step_millis: dict[str, int]
    total_millis: int


@dataclass(frozen=True)
class VoicePipeline:
    validator: WaveValidator
    limiter: UserRequestLimiter
    transcription: TranscriptionChain
    retriever: QuestionPassageRetriever
    prompt_builder: VoiceAnswerPromptBuilder
    answerer: GroqVoiceAnswerer
    grounding: AnswerGrounding
    speech: SpeechChain
    phrases: PhraseAudioCache
    history: VoiceExchangeRepository


class VoiceAssistantService:
    def __init__(self, pipeline: VoicePipeline, messages: VoiceMessages, history_turns: int,
                 total_timeout_seconds: float, clock=time.monotonic):
        self.pipeline = pipeline
        self.messages = messages
        self.history_turns = history_turns
        self.total_timeout_seconds = total_timeout_seconds
        self.clock = clock

    def warm_up(self, seconds: float) -> None:
        """Deja listo el audio de las frases fijas para no depender de Internet cuando hagan falta."""
        self.pipeline.phrases.warm([self.messages.unavailable, self.messages.abstention], seconds)

    def ask(self, context: VoiceQuestionContext, wav: bytes) -> VoiceAnswer:
        """Lanza VoiceRequestError por el audio o los límites; cualquier falla del proveedor responde la
        frase fija, para que el jugador nunca se quede sin respuesta."""
        self.pipeline.validator.validate(wav)
        with self.pipeline.limiter.admit(context.user_id):
            answer = self._answer(context, wav)
        self._save(context, answer)
        return answer

    def _answer(self, context: VoiceQuestionContext, wav: bytes) -> VoiceAnswer:
        pipeline = self.pipeline
        deadline = Deadline(self.total_timeout_seconds, self.clock)
        times = StepTimes(self.clock)
        step = self.clock()
        try:
            transcript = pipeline.transcription.transcribe(wav, deadline)
        except TranscriptionUnavailable as error:
            log.warning("Voice question without transcription: %s", error)
            return self._unavailable(None, None, times, step, "transcription", deadline)
        step = times.measure("transcription", step)

        passages = pipeline.retriever.find(transcript.text)
        prompt = pipeline.prompt_builder.build(transcript.text, context.roast_state, context.knowledge_level,
                                               passages, self._history(context))
        step = times.measure("retrieval", step)

        if deadline.expired:
            return self._unavailable(transcript.text, transcript.provider, times, step, "answer", deadline)
        try:
            model_answer = pipeline.answerer.answer(prompt, deadline.remaining())
        except AnswerUnavailable as error:
            log.warning("Voice answer unavailable: %s", error)
            return self._unavailable(transcript.text, transcript.provider, times, step, "answer", deadline)
        grounded = pipeline.grounding.ground(model_answer, prompt)
        step = times.measure("answer", step)

        if grounded.status is AnswerStatus.ABSTAINED:
            audio = pipeline.phrases.get(grounded.text, deadline)
        else:
            audio = pipeline.speech.speak(grounded.text, deadline)
        times.measure("speech", step)
        return VoiceAnswer(grounded.status, transcript.text, grounded.text, grounded.citations, audio,
                           transcript.provider, pipeline.answerer.name, times.millis, times.total())

    def _unavailable(self, question: str | None, transcription_provider: str | None, times: StepTimes,
                     step: float, failed_step: str, deadline: Deadline) -> VoiceAnswer:
        step = times.measure(failed_step, step)
        audio = self.pipeline.phrases.get(self.messages.unavailable, deadline)
        times.measure("speech", step)
        return VoiceAnswer(AnswerStatus.UNAVAILABLE, question, self.messages.unavailable, [], audio,
                           transcription_provider, None, times.millis, times.total())

    def _history(self, context: VoiceQuestionContext) -> list[ConversationTurn]:
        try:
            return self.pipeline.history.recent_answered(context.user_id, context.roast_attempt_id,
                                                         self.history_turns)
        except Exception as error:
            log.warning("Could not read the voice history, answering without it: %s", error)
            return []

    def _save(self, context: VoiceQuestionContext, answer: VoiceAnswer) -> None:
        try:
            self.pipeline.history.save(VoiceExchange(
                context.user_id, context.roast_attempt_id, answer.question, answer.text, answer.status,
                answer.citations, answer.transcription_provider, answer.answer_provider,
                answer.audio.provider if answer.audio else None, answer.total_millis))
        except Exception as error:
            log.warning("Could not save the voice exchange: %s", error)
