import pytest

from app.voice.speech.base import SynthesisUnavailable, SynthesizedAudio
from app.voice.speech.chain import SpeechChain
from app.voice.speech.text_speech import SpeechTextRejected, TextSpeech
from tests.voice.wav_factory import tone_wav

WAV = tone_wav(0.3, rate=22_050)


class FakeSynthesizer:
    def __init__(self, name, error=None):
        self.name = name
        self.error = error
        self.texts = []

    def synthesize(self, text, time_budget_seconds):
        self.texts.append(text)
        if self.error:
            raise self.error
        return SynthesizedAudio(WAV, 22_050, self.name)


class FakeQuota:
    def __init__(self, allows=True):
        self.allows = allows
        self.reserved = 0

    def try_reserve(self, characters):
        if self.allows:
            self.reserved += characters
        return self.allows

    def release(self, characters):
        self.reserved -= characters


def speech_with(azure, piper, quota, max_chars=700, cache_max_entries=20):
    chain = SpeechChain([azure, piper], {"azure": quota})
    return TextSpeech(chain, max_chars, cache_max_entries, time_budget_seconds=5)


def test_reads_the_text_with_azure_and_counts_its_characters():
    azure, piper, quota = FakeSynthesizer("azure"), FakeSynthesizer("piper"), FakeQuota()

    audio = speech_with(azure, piper, quota).speak("  Tu nota final es 78 %.  ")

    assert audio.provider == "azure"
    assert azure.texts == ["Tu nota final es 78 %."]
    assert quota.reserved == len("Tu nota final es 78 %.")
    assert piper.texts == []


def test_uses_piper_when_the_month_of_azure_is_used_up():
    azure, piper = FakeSynthesizer("azure"), FakeSynthesizer("piper")

    audio = speech_with(azure, piper, FakeQuota(allows=False)).speak("Hola")

    assert audio.provider == "piper"
    assert azure.texts == []


def test_gives_back_the_characters_when_azure_fails():
    quota = FakeQuota()
    azure = FakeSynthesizer("azure", error=SynthesisUnavailable("down"))

    audio = speech_with(azure, FakeSynthesizer("piper"), quota).speak("Hola")

    assert audio.provider == "piper"
    assert quota.reserved == 0


def test_returns_none_when_no_voice_can_speak():
    failing = SynthesisUnavailable("down")

    audio = speech_with(FakeSynthesizer("azure", failing), FakeSynthesizer("piper", failing), FakeQuota()).speak("Hola")

    assert audio is None


def test_the_same_text_again_does_not_spend_characters():
    azure, quota = FakeSynthesizer("azure"), FakeQuota()
    speech = speech_with(azure, FakeSynthesizer("piper"), quota)

    first = speech.speak("Tu nota final es 78 %.")
    second = speech.speak("Tu nota final es 78 %.")

    assert first == second
    assert len(azure.texts) == 1
    assert quota.reserved == len("Tu nota final es 78 %.")


def test_forgets_the_oldest_text_when_the_memory_is_full():
    azure = FakeSynthesizer("azure")
    speech = speech_with(azure, FakeSynthesizer("piper"), FakeQuota(), cache_max_entries=2)

    for text in ["uno", "dos", "tres", "uno"]:
        speech.speak(text)

    assert azure.texts == ["uno", "dos", "tres", "uno"]


def test_does_not_remember_a_failed_reading():
    piper = FakeSynthesizer("piper", error=SynthesisUnavailable("down"))
    azure = FakeSynthesizer("azure", error=SynthesisUnavailable("down"))
    speech = speech_with(azure, piper, FakeQuota())

    speech.speak("Hola")
    azure.error = None

    assert speech.speak("Hola").provider == "azure"


@pytest.mark.parametrize("text", ["", "   ", "a" * 701])
def test_rejects_an_empty_or_too_long_text_without_spending_characters(text):
    azure, quota = FakeSynthesizer("azure"), FakeQuota()

    with pytest.raises(SpeechTextRejected):
        speech_with(azure, FakeSynthesizer("piper"), quota).speak(text)

    assert azure.texts == []
    assert quota.reserved == 0


def test_accepts_a_text_of_exactly_the_maximum_length():
    assert speech_with(FakeSynthesizer("azure"), FakeSynthesizer("piper"), FakeQuota()).speak("a" * 700) is not None
