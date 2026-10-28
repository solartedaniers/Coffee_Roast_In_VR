import json

import pytest

from app.config import SERVICE_ROOT
from app.rag.citations import Citation, CitationResolver, RetrievedPassage, load_document_titles
from app.rag.pdf_catalog import PdfCatalog
from app.rag.vector_store import ScoredChunk
from tests.support.pdf_factory import write_text_pdf

TITLES_FILE = SERVICE_ROOT / "rag-docs" / "document_titles.json"


@pytest.fixture
def resolver(tmp_path) -> CitationResolver:
    write_text_pdf(tmp_path / "a.pdf", ["uno", "dos"])
    write_text_pdf(tmp_path / "sin-titulo.pdf", ["uno"])
    return CitationResolver(PdfCatalog(tmp_path), {"a.pdf": "Manual de tueste"})


def chunk(metadata):
    return ScoredChunk("texto", metadata, 0.8)


def test_cites_the_file_page_and_readable_title(resolver):
    assert resolver.cite(chunk({"file_name": "a.pdf", "page_number": 2})) == Citation("a.pdf", 2, "Manual de tueste")


def test_uses_the_file_name_when_there_is_no_title(resolver):
    assert resolver.cite(chunk({"file_name": "sin-titulo.pdf", "page_number": 1})).title == "sin-titulo"


@pytest.mark.parametrize("metadata", [
    {"file_name": "a.pdf", "page_number": 3},
    {"file_name": "a.pdf", "page_number": 0},
    {"file_name": "borrado.pdf", "page_number": 1},
    {"file_name": "../a.pdf", "page_number": 1},
    {"file_name": "a.pdf"},
    {"file_name": "a.pdf", "page_number": "2"},
    {"page_number": 1},
])
def test_never_cites_a_source_or_page_that_does_not_exist(resolver, metadata):
    assert resolver.cite(chunk(metadata)) is None


def test_keeps_the_text_even_without_a_citation(resolver):
    passages = resolver.passages([chunk({"file_name": "borrado.pdf", "page_number": 1})])

    assert passages == [RetrievedPassage("texto", None)]


def test_rejects_a_titles_file_with_the_wrong_shape(tmp_path):
    path = tmp_path / "titles.json"
    path.write_text(json.dumps(["no es un mapa"]), encoding="utf-8")

    with pytest.raises(ValueError):
        load_document_titles(path)
    assert load_document_titles(tmp_path / "missing.json") == {}


def test_every_pdf_in_rag_docs_has_a_title_and_every_title_has_its_pdf():
    titles = load_document_titles(TITLES_FILE)

    assert set(titles) == set(PdfCatalog(SERVICE_ROOT / "rag-docs").file_names())
    assert all(title.strip() for title in titles.values())
