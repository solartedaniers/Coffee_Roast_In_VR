import logging
from contextlib import contextmanager

import pytest

from app.container import build_chunk_search
from app.rag import chunk_search as chunk_search_module
from app.rag.chunk_search import SemanticChunkSearch
from app.rag.citations import Citation, CitationResolver, RetrievedPassage
from app.rag.context_retriever import RagContextRetriever
from app.rag.embeddings import EmbeddingError
from app.rag.pdf_catalog import PdfCatalog
from app.rag.query_builder import RagQueryBuilder
from app.rag.vector_store import ScoredChunk
from app.roast_reference import DevelopmentTimeRange
from tests.support.pdf_factory import write_text_pdf
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


class FakeCitations:
    def passages(self, chunks):
        return [RetrievedPassage(chunk.text, None) for chunk in chunks]


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
        return RagContextRetriever(search, query_builder, settings.rag_top_k, FakeCitations())

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


def test_adds_the_query_prefix_only_to_the_embedded_text(monkeypatch):
    monkeypatch.setattr(chunk_search_module, "PgVectorStore", FakeStore)
    embeddings = FakeEmbeddings()

    SemanticChunkSearch(FakeDatabase(), "table", embeddings, "search_query: ").search("¿Qué es el crack?", 4)

    assert embeddings.queries == ["search_query: ¿Qué es el crack?"]


def test_the_container_passes_the_configured_query_prefix(settings):
    configured = settings.model_copy(update={"rag_query_prefix": "search_query: "})

    assert build_chunk_search(settings, object(), object()).query_prefix == ""
    assert build_chunk_search(configured, object(), object()).query_prefix == "search_query: "


def test_returns_each_passage_with_its_validated_citation(monkeypatch, settings, tmp_path):
    write_text_pdf(tmp_path / "a.pdf", ["uno"])
    monkeypatch.setattr(chunk_search_module, "PgVectorStore", FakeStore)
    FakeStore.found = [ScoredChunk("Uno.", {"file_name": "a.pdf", "page_number": 1}, 0.8),
                       ScoredChunk("Dos.", {"file_name": "a.pdf", "page_number": 9}, 0.7)]
    query_builder = RagQueryBuilder(DevelopmentTimeRange(settings.rag_dtr_optimal_min, settings.rag_dtr_optimal_max))
    retriever = RagContextRetriever(SemanticChunkSearch(FakeDatabase(), "t", FakeEmbeddings()), query_builder, 4,
                                    CitationResolver(PdfCatalog(tmp_path), {"a.pdf": "Manual"}))

    passages = retriever.find_relevant_passages(ALL[0].request, ALL[0].level)

    assert passages == [RetrievedPassage("Uno.", Citation("a.pdf", 1, "Manual")), RetrievedPassage("Dos.", None)]
    assert retriever.find_relevant_chunks(ALL[0].request, ALL[0].level) == ["Uno.", "Dos."]
