from app.feedback.providers.base import ProviderUnavailable
from app.feedback.providers.chain import GeneratedFeedback, ProviderChain
from app.feedback.text_cleaner import FeedbackTextCleaner


class FakeClock:
    def __init__(self):
        self.now = 0.0

    def __call__(self):
        return self.now


class FakeProvider:
    def __init__(self, name, answer=None, error=False, clock=None, takes_seconds=0.0):
        self.name = name
        self.answer = answer
        self.error = error
        self.clock = clock
        self.takes_seconds = takes_seconds
        self.budgets = []

    def generate(self, prompt, time_budget_seconds):
        self.budgets.append(time_budget_seconds)
        if self.clock:
            self.clock.now += self.takes_seconds
        if self.error:
            raise ProviderUnavailable(f"{self.name} down")
        return self.answer


def chain(*providers, total=125.0, clock=None) -> ProviderChain:
    return ProviderChain(list(providers), FeedbackTextCleaner(), total, clock or FakeClock())


def test_uses_the_first_provider_that_answers():
    ollama = FakeProvider("ollama", "No se usa.")

    result = chain(FakeProvider("groq", "Buen tueste."), ollama).generate("prompt")

    assert result == GeneratedFeedback("Buen tueste.", "groq")
    assert ollama.budgets == []


def test_falls_back_to_the_next_provider_when_one_fails():
    result = chain(FakeProvider("groq", error=True), FakeProvider("ollama", "Buen tueste.")).generate("prompt")

    assert result == GeneratedFeedback("Buen tueste.", "ollama")


def test_cleans_the_text_and_skips_answers_that_end_up_empty():
    result = chain(FakeProvider("groq", "   "), FakeProvider("ollama", "Sube el gas.\n\nRepetido.")).generate("p")

    assert result == GeneratedFeedback("Sube el gas.", "ollama")


def test_returns_nothing_when_every_provider_fails():
    assert chain(FakeProvider("groq", error=True), FakeProvider("ollama", error=True)).generate("prompt") is None


def test_gives_each_provider_only_the_remaining_time():
    clock = FakeClock()
    groq = FakeProvider("groq", error=True, clock=clock, takes_seconds=15)
    ollama = FakeProvider("ollama", "Buen tueste.", clock=clock)

    chain(groq, ollama, total=125, clock=clock).generate("prompt")

    assert groq.budgets == [125]
    assert ollama.budgets == [110]


def test_stops_when_the_time_is_over():
    clock = FakeClock()
    ollama = FakeProvider("ollama", "Buen tueste.")

    result = chain(FakeProvider("groq", error=True, clock=clock, takes_seconds=125), ollama,
                   total=125, clock=clock).generate("prompt")

    assert result is None
    assert ollama.budgets == []
