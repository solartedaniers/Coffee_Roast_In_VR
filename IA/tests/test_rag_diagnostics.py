import pytest

from app.rag.diagnostics.index_diagnosis import FileStatus, classify, diagnose
from app.rag.diagnostics.index_statistics import FileChunkStats
from app.rag.diagnostics.pdf_inspector import PdfTextInspector, detect_language
from app.rag.ingested_document_repository import IngestedDocument
from tests.support.pdf_factory import write_text_pdf


def stats(file_name, chunks):
    return FileChunkStats(file_name, chunks, 500, 0, 0, 1)


def registered(file_name, chunks):
    return IngestedDocument(1, file_name, "h" * 64, chunks)


@pytest.mark.parametrize(("in_folder", "document", "stored", "expected"), [
    (True, registered("a.pdf", 3), stats("a.pdf", 3), FileStatus.OK),
    (True, None, None, FileStatus.NOT_INGESTED),
    (True, registered("a.pdf", 0), None, FileStatus.NO_CHUNKS),
    (True, registered("a.pdf", 5), stats("a.pdf", 3), FileStatus.COUNT_MISMATCH),
    (False, registered("a.pdf", 3), stats("a.pdf", 3), FileStatus.MISSING_PDF),
])
def test_classifies_each_file(in_folder, document, stored, expected):
    assert classify(in_folder, document, stored) is expected


def test_diagnoses_every_file_seen_in_any_source(tmp_path):
    inspector = PdfTextInspector(min_page_chars=3)
    report = inspector.inspect(write_text_pdf(tmp_path / "a.pdf", ["uno dos tres"]))

    files = diagnose([report], [registered("a.pdf", 1), registered("b.pdf", 2)], [stats("a.pdf", 1)])

    assert [(item.file_name, item.status) for item in files] == [("a.pdf", FileStatus.OK),
                                                                  ("b.pdf", FileStatus.MISSING_PDF)]


def test_reports_pages_without_text_and_where_references_start(tmp_path):
    path = write_text_pdf(tmp_path / "a.pdf", ["El tueste de la semilla", "", "Referencias"])

    report = PdfTextInspector(min_page_chars=5).inspect(path)

    assert report.pages == 3
    assert report.pages_without_text == [2]
    assert report.references_start_page == 3
    assert report.replacement_characters == 0
    assert report.language == "es"


def test_detects_the_language_by_its_common_words():
    assert detect_language("The roasting of the beans and the color") == "en"
    assert detect_language("El color de los granos en el tueste") == "es"
    assert detect_language("1234") == "unknown"
