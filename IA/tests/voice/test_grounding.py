import json

import pytest

from app.config import SERVICE_ROOT
from app.rag.citations import Citation, RetrievedPassage
from app.voice.answer.groq import ModelAnswer
from app.voice.answer.grounding import AnswerGrounding, SpokenTextLimiter
from app.voice.answer.prompt import AnswerPrompt
from app.voice.contracts import AnswerStatus
from app.voice.errors import VoiceErrorCode
from app.voice.messages import VoiceMessages

MANUAL = Citation("a.pdf", 12, "Manual")
GUIDE = Citation("b.pdf", 3, "Guía")
PROMPT = AnswerPrompt("s", "u", {"S1": RetrievedPassage("x", MANUAL), "S2": RetrievedPassage("y", GUIDE)})
ABSTENTION = "No tengo esa información."


def grounding(max_sentences=3, max_chars=450) -> AnswerGrounding:
    return AnswerGrounding(ABSTENTION, SpokenTextLimiter(max_sentences, max_chars))


def answer(text="El primer crack llega cerca de 196 °C.", ids=("S1",), sufficient=True) -> ModelAnswer:
    return ModelAnswer(sufficient=sufficient, answer=text, source_ids=list(ids))


def test_answers_with_the_citations_of_the_sources_it_used():
    grounded = grounding().ground(answer(ids=["S2", "ROAST", "S2"]), PROMPT)

    assert grounded.status is AnswerStatus.ANSWERED
    assert grounded.text == "El primer crack llega cerca de 196 °C."
    assert grounded.citations == [GUIDE]


def test_answers_from_the_roast_state_without_pdf_citations():
    grounded = grounding().ground(answer(ids=["ROAST"]), PROMPT)

    assert (grounded.status, grounded.citations) == (AnswerStatus.ANSWERED, [])


@pytest.mark.parametrize("model_answer", [
    answer(sufficient=False), answer(ids=[]), answer(ids=["S9"]), answer(ids=["S1", "INTERNET"]), answer(text="  "),
], ids=["insufficient", "no-sources", "invented-source", "mixed-invented", "empty"])
def test_abstains_without_real_evidence(model_answer):
    grounded = grounding().ground(model_answer, PROMPT)

    assert grounded.status is AnswerStatus.ABSTAINED
    assert (grounded.text, grounded.citations) == (ABSTENTION, [])


def test_keeps_at_most_the_configured_sentences_without_markup():
    text = "**Primero** baja el gas. Luego espera [S1]. Después mira el color! Y al final saca el café."

    assert SpokenTextLimiter(3, 450).limit(text) == "Primero baja el gas. Luego espera. Después mira el color!"


def test_keeps_whole_sentences_under_the_character_limit():
    assert SpokenTextLimiter(3, 30).limit("Baja el gas ahora. Espera el primer crack con calma.") == "Baja el gas ahora."


def test_cuts_a_single_long_sentence_at_a_whole_word():
    assert SpokenTextLimiter(3, 20).limit("Baja el gas despacio, sin apurarte nunca") == "Baja el gas despacio."


def test_reads_every_message_from_the_messages_file():
    messages = VoiceMessages.from_file(SERVICE_ROOT / "config" / "voice_messages.json")

    assert messages.unavailable.startswith("No puedo responder en este momento")
    assert all(messages.error(code) for code in VoiceErrorCode)


def test_rejects_a_messages_file_with_a_missing_error(tmp_path):
    path = tmp_path / "messages.json"
    path.write_text(json.dumps({"abstention": "a", "unavailable": "b", "errors": {}}), encoding="utf-8")

    with pytest.raises(ValueError, match="AUDIO_INVALID"):
        VoiceMessages.from_file(path)
