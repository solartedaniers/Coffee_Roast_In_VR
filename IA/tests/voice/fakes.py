"""Dobles de prueba de los tests de voz: nunca llaman a Groq, Azure, Piper ni Whisper."""


class FakeClock:
    def __init__(self, now: float = 1000.0):
        self.now = now

    def __call__(self) -> float:
        return self.now

    def advance(self, seconds: float) -> None:
        self.now += seconds


class FakeTranscriber:
    def __init__(self, name, result=None, error=None, clock=None, takes=0.0):
        self.name = name
        self.result = result
        self.error = error
        self.clock = clock
        self.takes = takes
        self.calls = 0

    def transcribe(self, wav, time_budget_seconds):
        self.calls += 1
        if self.clock:
            self.clock.advance(self.takes)
        if self.error:
            raise self.error
        return self.result
