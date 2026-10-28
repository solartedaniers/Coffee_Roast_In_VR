"""Decide si la respuesta del modelo se puede decir: solo con evidencia real y en formato para escuchar.

Como ningún umbral de similitud separó las preguntas sin respuesta (IA/eval/results/04), la decisión
no depende del puntaje del RAG sino de que el modelo cite evidencias que de verdad se le dieron.
"""
import logging
import re
from dataclasses import dataclass

from app.rag.citations import Citation
from app.voice.answer.groq import ModelAnswer
from app.voice.answer.prompt import AnswerPrompt
from app.voice.contracts import AnswerStatus

log = logging.getLogger(__name__)
SENTENCE_BOUNDARY = re.compile(r"(?<=[.!?…])\s+")
# Marcas de markdown o listas que no tienen sentido en voz.
MARKUP = re.compile(r"[*_#`>|]+|^\s*[-•]\s+|\[(S\d+|ROAST)\]", re.MULTILINE)
SPACE_BEFORE_PUNCTUATION = re.compile(r"\s+([.,;:!?…])")
SENTENCE_END = "."
WORD_BREAKS = " ,;:"


@dataclass(frozen=True)
class GroundedAnswer:
    status: AnswerStatus
    text: str
    citations: list[Citation]


class SpokenTextLimiter:
    """Deja el texto plano y corto: hasta max_sentences frases completas y max_chars caracteres."""

    def __init__(self, max_sentences: int, max_chars: int):
        self.max_sentences = max_sentences
        self.max_chars = max_chars

    def limit(self, text: str) -> str:
        plain = SPACE_BEFORE_PUNCTUATION.sub(r"\1", " ".join(MARKUP.sub(" ", text).split()))
        sentences = [sentence for sentence in SENTENCE_BOUNDARY.split(plain) if sentence][:self.max_sentences]
        kept: list[str] = []
        for sentence in sentences:
            if len(" ".join(kept + [sentence])) > self.max_chars:
                break
            kept.append(sentence)
        if kept:
            return " ".join(kept)
        if not sentences:
            return ""
        # Una sola frase más larga que el límite se corta en la última palabra completa.
        sentence = sentences[0]
        cut = sentence[:self.max_chars]
        if sentence[self.max_chars] not in WORD_BREAKS:
            cut = cut.rsplit(" ", 1)[0]
        return cut.rstrip(WORD_BREAKS) + SENTENCE_END


class AnswerGrounding:
    def __init__(self, abstention_text: str, limiter: SpokenTextLimiter):
        self.abstention_text = abstention_text
        self.limiter = limiter

    def ground(self, answer: ModelAnswer, prompt: AnswerPrompt) -> GroundedAnswer:
        if not answer.sufficient:
            return self.abstain()
        used = list(dict.fromkeys(answer.source_ids))
        unknown = [source_id for source_id in used if source_id not in prompt.evidence_ids]
        if not used or unknown:
            log.warning("Abstaining: the model cited %s, offered %s", used, sorted(prompt.evidence_ids))
            return self.abstain()
        text = self.limiter.limit(answer.answer)
        if not text:
            return self.abstain()
        citations = list(dict.fromkeys(
            prompt.passages[source_id].citation for source_id in used
            if source_id in prompt.passages and prompt.passages[source_id].citation))
        return GroundedAnswer(AnswerStatus.ANSWERED, text, citations)

    def abstain(self) -> GroundedAnswer:
        return GroundedAnswer(AnswerStatus.ABSTAINED, self.abstention_text, [])
