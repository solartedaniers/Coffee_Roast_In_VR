import json

import pytest

from app.config import SERVICE_ROOT
from app.rag.citations import (Citation, CitationResolver, Distribution, DocumentSource, RetrievedPassage,
                                load_document_sources)
from app.rag.pdf_catalog import PdfCatalog
from app.rag.vector_store import ScoredChunk
from tests.support.pdf_factory import write_text_pdf

TITLES_FILE = SERVICE_ROOT / "rag-docs" / "document_titles.json"
CITATION_FORMAT = "{authors} ({year}). {title}"
# Debe coincidir con la regla de IA/.gitignore.
LOCAL_PREFIX = "local-"


@pytest.fixture
def resolver(tmp_path) -> CitationResolver:
    write_text_pdf(tmp_path / "a.pdf", ["uno", "dos"])
    write_text_pdf(tmp_path / "sin-titulo.pdf", ["uno"])
    write_text_pdf(tmp_path / "manual.pdf", ["uno"])
    sources = {"a.pdf": DocumentSource("Curvas de tueste", "Osorio et al.", 2025),
               "manual.pdf": DocumentSource("Manual de tueste")}
    return CitationResolver(PdfCatalog(tmp_path), sources, CITATION_FORMAT)


def chunk(metadata):
    return ScoredChunk("texto", metadata, 0.8)


def test_cites_the_file_page_authors_year_and_title(resolver):
    assert resolver.cite(chunk({"file_name": "a.pdf", "page_number": 2})) == Citation(
        "a.pdf", 2, "Osorio et al. (2025). Curvas de tueste")


def test_cites_only_the_title_when_the_source_has_no_authors_or_year(resolver):
    assert resolver.cite(chunk({"file_name": "manual.pdf", "page_number": 1})).title == "Manual de tueste"


def test_rejects_a_citation_format_with_an_unknown_field(tmp_path):
    with pytest.raises(KeyError):
        CitationResolver(PdfCatalog(tmp_path), {}, "{authors} - {publisher}")


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


@pytest.mark.parametrize("content", [
    ["no es un mapa"],
    {"a.pdf": "solo el título"},
    {"a.pdf": {"title": " "}},
    {"a.pdf": {"title": "T", "year": "2020"}},
    {"a.pdf": {"title": "T", "authors": 3}},
    {"a.pdf": {"title": "T", "distribution": "github"}},
])
def test_rejects_a_titles_file_with_the_wrong_shape(tmp_path, content):
    path = tmp_path / "titles.json"
    path.write_text(json.dumps(content), encoding="utf-8")

    with pytest.raises(ValueError):
        load_document_sources(path)


def test_without_a_titles_file_there_are_no_sources(tmp_path):
    assert load_document_sources(tmp_path / "missing.json") == {}


def test_every_pdf_in_rag_docs_has_a_title_and_only_local_pdfs_may_be_missing():
    # En el CI los PDFs locales no están (git los ignora); en mi máquina y en el servidor sí.
    sources = load_document_sources(TITLES_FILE)
    present = set(PdfCatalog(SERVICE_ROOT / "rag-docs").file_names())

    assert present <= set(sources)
    assert all(sources[name].distribution is Distribution.LOCAL for name in set(sources) - present)


def test_only_local_pdfs_use_the_prefix_that_git_ignores():
    sources = load_document_sources(TITLES_FILE)

    assert all(name.startswith(LOCAL_PREFIX) == (source.distribution is Distribution.LOCAL)
               for name, source in sources.items())
