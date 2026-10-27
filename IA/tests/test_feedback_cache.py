from app.contracts import RoastFeedbackRequest
from app.domain import KnowledgeLevel, RoastingResult
from app.feedback.cache import FeedbackCache


class FakeClock:
    def __init__(self):
        self.now = 0.0

    def __call__(self):
        return self.now


def test_returns_a_stored_value_until_it_expires():
    clock = FakeClock()
    cache = FeedbackCache(ttl_seconds=86400, max_entries=500, clock=clock)
    cache.put("k", "Buen tueste.")

    clock.now = 86399
    assert cache.get("k") == "Buen tueste."
    clock.now = 86400
    assert cache.get("k") is None


def test_drops_the_least_recently_used_entry_when_full():
    cache = FeedbackCache(ttl_seconds=60, max_entries=2, clock=FakeClock())
    cache.put("a", "A")
    cache.put("b", "B")
    cache.get("a")

    cache.put("c", "C")

    assert (cache.get("a"), cache.get("b"), cache.get("c")) == ("A", None, "C")


def test_can_be_cleared():
    cache = FeedbackCache(ttl_seconds=60, max_entries=2, clock=FakeClock())
    cache.put("a", "A")

    cache.clear()

    assert cache.get("a") is None


def test_is_disabled_with_zero_ttl_or_size():
    for cache in (FeedbackCache(0, 500), FeedbackCache(60, 0)):
        cache.put("a", "A")
        assert cache.get("a") is None


def test_keys_depend_on_the_session_the_level_and_the_configuration():
    session = RoastFeedbackRequest(result=RoastingResult.PERFECT, quality_score=92,
                                   knowledge_level=KnowledgeLevel.BEGINNER)

    same = FeedbackCache.key_for(session, "groq:openai/gpt-oss-120b")
    assert same == FeedbackCache.key_for(session.model_copy(), "groq:openai/gpt-oss-120b")
    assert same != FeedbackCache.key_for(session, "groq:openai/gpt-oss-20b")
    assert same != FeedbackCache.key_for(session.model_copy(update={"knowledge_level": KnowledgeLevel.ADVANCED}),
                                         "groq:openai/gpt-oss-120b")
