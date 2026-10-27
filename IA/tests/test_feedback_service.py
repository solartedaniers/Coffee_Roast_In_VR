import pytest

from app.contracts import RoastFeedbackRequest
from app.feedback.cache import FeedbackCache
from app.feedback.prompt_builder import FeedbackPromptBuilder
from app.feedback.providers.chain import GeneratedFeedback
from app.feedback.service import FeedbackService
from app.roast_reference import DevelopmentTimeRange
from tests.support.recorded_sessions import ALL


class FakeRetriever:
    def __init__(self, chunks):
        self.chunks = chunks
        self.calls = []

    def find_relevant_chunks(self, session, level):
        self.calls.append(level)
        return self.chunks


class FakeChain:
    def __init__(self, result):
        self.result = result
        self.prompts = []

    def generate(self, prompt):
        self.prompts.append(prompt)
        return self.result


@pytest.fixture
def builder(settings):
    return FeedbackPromptBuilder(DevelopmentTimeRange(settings.rag_dtr_optimal_min, settings.rag_dtr_optimal_max))


def service(builder, chain, retriever=None, cache=None):
    return FeedbackService(retriever or FakeRetriever(["Fragmento uno.", "Fragmento dos."]), builder, chain,
                           cache or FeedbackCache(86400, 500), "groq:openai/gpt-oss-120b")


def test_generates_feedback_with_the_level_sent_by_the_backend(builder):
    recorded = ALL[0]
    session = recorded.request.model_copy(update={"knowledge_level": recorded.level})
    retriever = FakeRetriever(recorded.context)
    chain = FakeChain(GeneratedFeedback("Buen tueste.", "groq"))

    feedback = service(builder, chain, retriever).generate_feedback(session)

    assert feedback == "Buen tueste."
    assert retriever.calls == [recorded.level]
    assert chain.prompts == [recorded.expected_prompt()]


def test_answers_none_when_no_provider_answers(builder):
    assert service(builder, FakeChain(None)).generate_feedback(ALL[0].request) is None


def test_answers_none_when_the_session_cannot_be_described(builder):
    assert service(builder, FakeChain(GeneratedFeedback("x", "groq"))).generate_feedback(RoastFeedbackRequest()) is None


def test_reuses_cached_feedback_without_calling_the_providers_again(builder):
    chain = FakeChain(GeneratedFeedback("Buen tueste.", "groq"))
    feedback_service = service(builder, chain)

    first = feedback_service.generate_feedback(ALL[0].request)
    second = feedback_service.generate_feedback(ALL[0].request)

    assert first == second == "Buen tueste."
    assert len(chain.prompts) == 1


def test_does_not_cache_a_missing_answer(builder):
    chain = FakeChain(None)
    feedback_service = service(builder, chain)

    feedback_service.generate_feedback(ALL[0].request)
    feedback_service.generate_feedback(ALL[0].request)

    assert len(chain.prompts) == 2
