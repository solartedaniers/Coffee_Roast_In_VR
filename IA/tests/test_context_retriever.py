import logging
from contextlib import contextmanager

import pytest

from app.rag import chunk_search as chunk_search_module
from app.rag.chunk_search import SemanticChunkSearch
from app.rag.context_retriever import RagContextRetriever
from app.rag.embeddings import EmbeddingError
from app.rag.query_builder import RagQueryBuilder
from app.rag.vector_store import ScoredChunk
from app.roast_reference import DevelopmentTimeRange
from tests.support.recorded_sessions import ALL


class FakeEmbeddings:
    def __init__(self, error: Exception | None = None):
        self.error = error
        self.queries = []

    def embed_one(self, text):
        if self.error:
            raise self.error
        self.queries.append(text)
        return [0.1, 0.2, 0.3]


class FakeDatabase:
    @contextmanager
    def transaction(self):
        yield object()


class FakeStore:
    found: list[ScoredChunk] = []
    searches = []

    def __init__(self, connection, table):
        self.table = table

    def similarity_search(self, vector, top_k):
        FakeStore.searches.append((self.table, vector, top_k))
        return FakeStore.found


@pytest.fixture
def retriever_with(monkeypatch, settings):
    monkeypatch.setattr(chunk_search_module, "PgVectorStore", FakeStore)
    FakeStore.searches = []

    def build(embeddings) -> RagContextRetriever:
        query_builder = RagQueryBuilder(DevelopmentTimeRange(settings.rag_dtr_optimal_min, settings.rag_dtr_optimal_max))
        search = SemanticChunkSearch(FakeDatabase(), settings.rag_vector_table, embeddings)
        return RagContextRetriever(search, query_builder, settings.rag_top_k)

    return build


def test_searches_with_the_session_query_and_returns_the_chunk_texts(retriever_with, caplog):
    FakeStore.found = [ScoredChunk("Fragmento uno.", {"file_name": "a.pdf"}, 0.8), ScoredChunk("Dos.", {}, 0.5)]
    embeddings = FakeEmbeddings()
    recorded = ALL[0]

    with caplog.at_level(logging.INFO):
        chunks = retriever_with(embeddings).find_relevant_chunks(recorded.request, recorded.level)

    assert chunks == ["Fragmento uno.", "Dos."]
    assert embeddings.queries == [recorded.expected_query().split("\ntopK=")[0]]
    assert FakeStore.searches == [("rag_document_chunks", [0.1, 0.2, 0.3], 4)]
    assert "file=a.pdf" in caplog.text and "file=unknown" in caplog.text


def test_returns_no_chunks_when_retrieval_fails(retriever_with):
    chunks = retriever_with(FakeEmbeddings(EmbeddingError("down"))).find_relevant_chunks(ALL[0].request, None)

    assert chunks == []
