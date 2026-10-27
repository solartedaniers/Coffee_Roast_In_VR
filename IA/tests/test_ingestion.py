from contextlib import contextmanager

import pytest

from app.contracts import IngestionStatus
from app.rag import ingestion_service as module
from app.rag.document import Document
from app.rag.ingested_document_repository import IngestedDocument
from app.rag.ingestion_service import RagIngestionService
from app.rag.pdf_reader import PdfPageReader
from app.rag.text_splitter import CharacterTextSplitter
from tests.support.pdf_factory import write_text_pdf


def test_reads_one_document_per_page(tmp_path):
    pdf = write_text_pdf(tmp_path / "manual.pdf", ["Primera pagina", "Segunda pagina"])

    pages = PdfPageReader().read(pdf)

    assert [page.metadata for page in pages] == [
        {"page_number": 1, "file_name": "manual.pdf"}, {"page_number": 2, "file_name": "manual.pdf"},
    ]
    assert "Primera pagina" in pages[0].text
    assert "Segunda pagina" in pages[1].text


class FakeDatabase:
    def __init__(self):
        self.transactions = 0

    @contextmanager
    def transaction(self):
        self.transactions += 1
        yield object()


class FakeState:
    def __init__(self):
        self.records: dict[str, IngestedDocument] = {}
        self.chunks: list[Document] = []
        self.fail_on_add = False


class FakeRepository:
    state: FakeState

    def __init__(self, connection):
        pass

    def find_by_file_name(self, file_name):
        return self.state.records.get(file_name)

    def save_new(self, file_name, content_hash, chunk_count):
        self.state.records[file_name] = IngestedDocument(len(self.state.records) + 1, file_name, content_hash, chunk_count)

    def mark_reingested(self, document_id, content_hash, chunk_count):
        old = next(record for record in self.state.records.values() if record.id == document_id)
        self.state.records[old.file_name] = IngestedDocument(document_id, old.file_name, content_hash, chunk_count)


class FakeStore:
    state: FakeState

    def __init__(self, connection, table):
        pass

    def delete_by_file_name(self, file_name):
        self.state.chunks = [chunk for chunk in self.state.chunks if chunk.metadata["file_name"] != file_name]

    def add(self, documents, vectors):
        if self.state.fail_on_add:
            raise RuntimeError("insert failed")
        self.state.chunks.extend(documents)


class FakeEmbeddings:
    def __init__(self):
        self.batches = []

    def embed(self, texts):
        self.batches.append(len(texts))
        return [[0.0, 0.0, 1.0] for _ in texts]


@pytest.fixture
def state(monkeypatch) -> FakeState:
    current = FakeState()
    FakeRepository.state = current
    FakeStore.state = current
    monkeypatch.setattr(module, "IngestedDocumentRepository", FakeRepository)
    monkeypatch.setattr(module, "PgVectorStore", FakeStore)
    return current


def service_for(docs_dir, embeddings=None, batch_size=32) -> RagIngestionService:
    return RagIngestionService(FakeDatabase(), "rag_document_chunks", docs_dir, PdfPageReader(),
                               CharacterTextSplitter(700, 100), embeddings or FakeEmbeddings(), batch_size)


def test_ingests_new_pdfs_with_their_hash_and_page(tmp_path, state):
    write_text_pdf(tmp_path / "b.pdf", ["Tueste"])
    write_text_pdf(tmp_path / "a.pdf", ["Secado", "Maillard"])

    summary = service_for(tmp_path).ingest_all()

    assert [(file.file_name, file.status, file.chunk_count) for file in summary.files] == [
        ("a.pdf", IngestionStatus.INGESTED, 2), ("b.pdf", IngestionStatus.INGESTED, 1),
    ]
    assert summary.total_chunks_indexed == 3
    first = state.chunks[0].metadata
    assert first["file_name"] == "a.pdf" and first["page_number"] == 1
    assert first["content_hash"] == state.records["a.pdf"].content_hash
    assert {"parent_document_id", "chunk_index", "total_chunks"} <= first.keys()


def test_skips_unchanged_pdfs(tmp_path, state):
    write_text_pdf(tmp_path / "a.pdf", ["Secado"])
    service = service_for(tmp_path)
    service.ingest_all()

    summary = service.ingest_all()

    assert summary.files[0].status is IngestionStatus.SKIPPED_UNCHANGED
    assert summary.files_skipped == 1
    assert len(state.chunks) == 1


def test_replaces_the_chunks_of_a_changed_pdf(tmp_path, state):
    service = service_for(tmp_path)
    write_text_pdf(tmp_path / "a.pdf", ["Version uno"])
    service.ingest_all()
    write_text_pdf(tmp_path / "a.pdf", ["Version dos", "Pagina nueva"])

    summary = service.ingest_all()

    assert summary.files[0].status is IngestionStatus.REINGESTED
    assert summary.files_reingested == 1
    assert ["Version dos" in chunk.text or "Pagina nueva" in chunk.text for chunk in state.chunks] == [True, True]
    assert state.records["a.pdf"].chunk_count == 2


def test_marks_a_broken_pdf_as_failed_and_continues(tmp_path, state):
    (tmp_path / "a.pdf").write_bytes(b"not a pdf")
    write_text_pdf(tmp_path / "b.pdf", ["Secado"])

    summary = service_for(tmp_path).ingest_all()

    assert summary.files[0].status is IngestionStatus.FAILED
    assert summary.files[0].error_message
    assert summary.files[1].status is IngestionStatus.INGESTED
    assert summary.files_failed == 1
    assert "a.pdf" not in state.records


def test_does_not_record_a_pdf_whose_chunks_were_not_stored(tmp_path, state):
    write_text_pdf(tmp_path / "a.pdf", ["Secado"])
    state.fail_on_add = True

    summary = service_for(tmp_path).ingest_all()

    assert summary.files[0].status is IngestionStatus.FAILED
    assert "a.pdf" not in state.records


def test_embeds_in_batches(tmp_path, state):
    write_text_pdf(tmp_path / "a.pdf", [f"Pagina {number}" for number in range(5)])
    embeddings = FakeEmbeddings()

    service_for(tmp_path, embeddings, batch_size=2).ingest_all()

    assert embeddings.batches == [2, 2, 1]


def test_returns_an_empty_summary_without_pdfs(tmp_path, state):
    summary = service_for(tmp_path / "missing").ingest_all()

    assert summary.files == []
