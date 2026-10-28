from app.voice.deadline import Deadline
from app.voice.speech.base import SynthesisUnavailable, SynthesizedAudio
from app.voice.speech.chain import SpeechChain
from tests.voice.fakes import FakeClock


class FakeSynthesizer:
    def __init__(self, name, error=None, clock=None, takes=0.0):
        self.name = name
        self.error = error
        self.clock = clock
        self.takes = takes
        self.texts = []

    def synthesize(self, text, time_budget_seconds):
        self.texts.append(text)
        if self.clock:
            self.clock.advance(self.takes)
        if self.error:
            raise self.error
        return SynthesizedAudio(b"RIFF" + self.name.encode(), 22050, self.name)


class FakeQuota:
    def __init__(self, limit=1000, error=None):
        self.limit = limit
        self.used = 0
        self.error = error

    def try_reserve(self, characters):
        if self.error:
            raise self.error
        if self.used + characters > self.limit:
            return False
        self.used += characters
        return True

    def release(self, characters):
        self.used -= characters


def test_uses_azure_and_counts_its_characters():
    azure, piper, quota = FakeSynthesizer("azure"), FakeSynthesizer("piper"), FakeQuota()

    audio = SpeechChain([azure, piper], {"azure": quota}).speak("Baja el gas.", Deadline(10))

    assert audio.provider == "azure"
    assert quota.used == len("Baja el gas.")
    assert piper.texts == []


def test_goes_straight_to_piper_when_the_month_is_used_up():
    azure, piper = FakeSynthesizer("azure"), FakeSynthesizer("piper")

    audio = SpeechChain([azure, piper], {"azure": FakeQuota(limit=5)}).speak("Baja el gas.", Deadline(10))

    assert audio.provider == "piper"
    assert azure.texts == []


def test_gives_back_the_characters_when_azure_fails():
    quota = FakeQuota()

    audio = SpeechChain([FakeSynthesizer("azure", error=SynthesisUnavailable("503")), FakeSynthesizer("piper")],
                        {"azure": quota}).speak("Baja el gas.", Deadline(10))

    assert audio.provider == "piper"
    assert quota.used == 0


def test_skips_azure_when_the_counter_cannot_be_checked():
    azure = FakeSynthesizer("azure")

    audio = SpeechChain([azure, FakeSynthesizer("piper")], {"azure": FakeQuota(error=ConnectionError("db"))}).speak(
        "hola", Deadline(10))

    assert audio.provider == "piper"
    assert azure.texts == []


def test_returns_no_audio_when_every_voice_fails():
    chain = SpeechChain([FakeSynthesizer("azure", error=SynthesisUnavailable("x")),
                         FakeSynthesizer("piper", error=SynthesisUnavailable("y"))], {"azure": FakeQuota()})

    assert chain.speak("hola", Deadline(10)) is None


def test_does_not_start_another_voice_after_the_deadline():
    clock = FakeClock()
    piper = FakeSynthesizer("piper")
    slow = FakeSynthesizer("azure", error=SynthesisUnavailable("lento"), clock=clock, takes=11)

    assert SpeechChain([slow, piper], {}).speak("hola", Deadline(10, clock)) is None
    assert piper.texts == []
