from app.api import warm_up
from app.container import Services, build_services
from app.feedback.cache import FeedbackCache


class Recorder:
    def __init__(self, name, calls):
        self.name, self.calls = name, calls

    def warm_up(self, seconds):
        self.calls.append((self.name, seconds))


def test_warms_the_assistant_phrases_and_then_the_alerts():
    calls = []
    services = Services(None, None, FeedbackCache(60, 10), None, voice=Recorder("voice", calls),
                        voice_warm_up_seconds=25.0, alerts=Recorder("alerts", calls))
    warm_up(services)
    assert calls == [("voice", 25.0), ("alerts", 25.0)]


def test_warm_up_skips_what_is_not_configured():
    warm_up(Services(None, None, FeedbackCache(60, 10), None))


def test_the_alerts_share_the_phrase_cache_of_the_assistant(settings):
    services = build_services(settings)
    assert services.alerts.phrases is services.voice.pipeline.phrases
    assert services.alerts.catalog.catalog_revision >= 1
