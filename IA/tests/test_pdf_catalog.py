import pytest

from app.rag.pdf_catalog import PdfCatalog
from tests.support.pdf_factory import write_text_pdf


@pytest.fixture
def catalog(tmp_path) -> PdfCatalog:
    write_text_pdf(tmp_path / "a.pdf", ["uno", "dos"])
    return PdfCatalog(tmp_path)


def test_the_catalog_knows_the_pages_of_each_pdf(catalog):
    assert catalog.file_names() == ["a.pdf"]
    assert catalog.page_count("a.pdf") == 2
    assert catalog.contains_page("a.pdf", 2)
    assert not catalog.contains_page("a.pdf", 3)
    assert not catalog.contains_page("a.pdf", 0)
    assert catalog.page_count("missing.pdf") is None


def test_the_catalog_ignores_names_with_paths(catalog):
    assert catalog.page_count("../a.pdf") is None
