from app.voice.deadline import Deadline
from app.voice.speech.base import SynthesizedAudio
from app.voice.speech.phrase_cache import PhraseAudioCache
from tests.voice.wav_factory import tone_wav

WAV = tone_wav(0.3, rate=22_050)


class CountingChain:
    def __init__(self, audio=SynthesizedAudio(WAV, 22_050, "azure")):
        self.audio = audio
        self.texts = []

    def speak(self, text, deadline):
        self.texts.append(text)
        return self.audio


def test_synthesizes_a_phrase_once_and_then_serves_it_from_memory(tmp_path):
    chain = CountingChain()
    cache = PhraseAudioCache(chain, tmp_path, "azure:es-CO-SalomeNeural")

    first = cache.get("No puedo responder.", Deadline(5))
    second = cache.get("No puedo responder.", Deadline(5))

    assert first == second == SynthesizedAudio(WAV, 22_050, "azure")
    assert chain.texts == ["No puedo responder."]


def test_survives_a_restart_by_reading_the_disk(tmp_path):
    PhraseAudioCache(CountingChain(), tmp_path, "v1").get("hola", Deadline(5))
    chain = CountingChain()

    audio = PhraseAudioCache(chain, tmp_path, "v1").get("hola", Deadline(5))

    assert (audio.wav, audio.provider) == (WAV, "azure")
    assert chain.texts == []


def test_a_new_voice_does_not_reuse_the_old_audio(tmp_path):
    PhraseAudioCache(CountingChain(), tmp_path, "v1").get("hola", Deadline(5))
    chain = CountingChain()

    PhraseAudioCache(chain, tmp_path, "v2").get("hola", Deadline(5))

    assert chain.texts == ["hola"]


def test_does_not_store_a_failure_and_retries_later(tmp_path):
    failing = CountingChain(audio=None)
    cache = PhraseAudioCache(failing, tmp_path, "v1")

    assert cache.get("hola", Deadline(5)) is None
    assert cache.get("hola", Deadline(5)) is None
    assert failing.texts == ["hola", "hola"]
    assert list(tmp_path.iterdir()) == []


def test_ignores_a_corrupt_file_and_synthesizes_again(tmp_path):
    cache = PhraseAudioCache(CountingChain(), tmp_path, "v1")
    (tmp_path / f"{cache.key('hola')}.azure.wav").write_bytes(b"roto")

    assert cache.get("hola", Deadline(5)).wav == WAV


def test_warms_the_fixed_phrases(tmp_path):
    chain = CountingChain()

    PhraseAudioCache(chain, tmp_path, "v1").warm(["a", "b"], 5)

    assert chain.texts == ["a", "b"]
