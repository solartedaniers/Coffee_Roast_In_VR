from app.container import build_chunk_search
from app.feedback.providers.base import ProviderUnavailable
from app.rag.chunk_search import SemanticChunkSearch, TranslatedChunkSearch
from app.rag.query_translator import QueryTranslator
from app.rag.vector_store import ScoredChunk


class FakeProvider:
    def __init__(self, answer=None, error=None):
        self.answer = answer
        self.error = error
        self.prompts = []

    def generate(self, prompt, time_budget_seconds):
        self.prompts.append((prompt, time_budget_seconds))
        if self.error:
            raise self.error
        return self.answer


class FakeTranslator:
    def __init__(self, translation):
        self.translation = translation

    def translate(self, query):
        return self.translation


class FakeSearch:
    def __init__(self, results):
        self.results = results
        self.queries = []

    def search(self, query, top_k):
        self.queries.append(query)
        return self.results.get(query, [])[:top_k]


def chunk(text, score, page=1):
    return ScoredChunk(text, {"file_name": "a.pdf", "page_number": page}, score)


def test_asks_for_the_translation_and_keeps_its_first_line():
    provider = FakeProvider(answer='\n"What is the first crack?"\nExtra line')

    translated = QueryTranslator(provider, "English", 15.0).translate("¿Qué es el primer crack?")

    assert translated == "What is the first crack?"
    prompt, budget = provider.prompts[0]
    assert "English" in prompt and "¿Qué es el primer crack?" in prompt and budget == 15.0


def test_returns_none_when_the_provider_fails():
    assert QueryTranslator(FakeProvider(error=ProviderUnavailable("down")), "English", 1).translate("q") is None


def test_merges_both_searches_keeping_the_best_similarity_of_each_chunk():
    inner = FakeSearch({"crack": [chunk("es", 0.7), chunk("same", 0.5)],
                        "crack en": [chunk("en", 0.9), chunk("same", 0.6, page=1)]})

    found = TranslatedChunkSearch(inner, FakeTranslator("crack en")).search("crack", 3)

    assert inner.queries == ["crack", "crack en"]
    assert [(item.text, item.score) for item in found] == [("en", 0.9), ("es", 0.7), ("same", 0.6)]


def test_searches_once_when_there_is_no_translation():
    inner = FakeSearch({"crack": [chunk("es", 0.7)]})

    found = TranslatedChunkSearch(inner, FakeTranslator(None)).search("crack", 3)

    assert inner.queries == ["crack"]
    assert [item.text for item in found] == ["es"]


def test_the_container_wraps_the_search_only_when_translation_is_on(settings):
    assert isinstance(build_chunk_search(settings, object(), object()), SemanticChunkSearch)

    translated = build_chunk_search(settings.model_copy(update={"rag_query_translation_enabled": True}),
                                    object(), object())

    assert isinstance(translated, TranslatedChunkSearch)
    assert translated.translator.target_language == "English"
