from app.rag.citations import Citation, RetrievedPassage
from app.rag.vector_store import ScoredChunk
from app.voice.answer.retriever import QuestionPassageRetriever

CITATION = Citation("tueste.pdf", 3, "Manual de tueste")


class FakeSearch:
    def __init__(self, chunks=(), error=None):
        self.chunks = list(chunks)
        self.error = error
        self.calls = []

    def search(self, query, top_k):
        self.calls.append((query, top_k))
        if self.error:
            raise self.error
        return self.chunks[:top_k]


class FakeCitations:
    def passages(self, chunks):
        return [RetrievedPassage(chunk.text, CITATION if chunk.metadata.get("ok") else None) for chunk in chunks]


def chunk(text, ok=True):
    return ScoredChunk(text, {"ok": ok}, 0.7)


def test_searches_with_the_question_and_its_own_top_k():
    search = FakeSearch([chunk("a"), chunk("b")])

    passages = QuestionPassageRetriever(search, FakeCitations(), 3).find("¿Qué es el primer crack?")

    assert search.calls == [("¿Qué es el primer crack?", 3)]
    assert passages == [RetrievedPassage("a", CITATION), RetrievedPassage("b", CITATION)]


def test_drops_passages_without_a_validated_citation():
    passages = QuestionPassageRetriever(FakeSearch([chunk("a", ok=False), chunk("b")]), FakeCitations(), 3).find("q")

    assert [passage.text for passage in passages] == ["b"]


def test_continues_without_sources_when_the_search_fails():
    retriever = QuestionPassageRetriever(FakeSearch(error=ConnectionError("db down")), FakeCitations(), 3)

    assert retriever.find("q") == []
