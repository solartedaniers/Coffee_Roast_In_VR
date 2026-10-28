import pytest

from app.config import SERVICE_ROOT
from app.voice.silence import SilencePhraseFilter, plain_text

FILTER = SilencePhraseFilter.from_file(SERVICE_ROOT / "config" / "whisper_silence_phrases.json")


@pytest.mark.parametrize("transcript", [
    "¡Gracias por ver el video!", "Subtítulos realizados por la comunidad de Amara.org", "Suscríbete al canal.",
    "Música", "Gracias.", "  ", "...", "Gracias por ver. Gracias.",
])
def test_detects_whisper_silence_phrases(transcript):
    assert FILTER.is_silence(transcript)


@pytest.mark.parametrize("transcript", [
    "¿Cuándo llega el primer crack?", "Gracias, ¿y ahora subo el gas?", "¿Qué música suena en el tostador?",
])
def test_keeps_real_questions(transcript):
    assert not FILTER.is_silence(transcript)


def test_compares_without_accents_case_or_punctuation():
    assert plain_text("¿Qué  TEMPERATURA, señor?") == "que temperatura senor"


def test_rejects_a_file_without_patterns(tmp_path):
    path = tmp_path / "phrases.json"
    path.write_text('{"patterns": []}', encoding="utf-8")

    with pytest.raises(ValueError):
        SilencePhraseFilter.from_file(path)
