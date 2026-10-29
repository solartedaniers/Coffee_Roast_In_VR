"""Citas (archivo, página y título) de los fragmentos recuperados, para mostrarlas o decirlas.

Una cita solo se entrega si el archivo existe en rag-docs/ y la página está dentro del PDF: así
el asistente nunca cita una fuente o una página que no existe.
"""
import json
import logging
from dataclasses import dataclass
from pathlib import Path

from app.rag.pdf_catalog import PdfCatalog
from app.rag.pdf_reader import FILE_NAME_KEY, PAGE_NUMBER_KEY
from app.rag.vector_store import ScoredChunk

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class Citation:
    file_name: str
    page_number: int
    title: str


@dataclass(frozen=True)
class RetrievedPassage:
    text: str
    citation: Citation | None


@dataclass(frozen=True)
class DocumentSource:
    """Datos bibliográficos de un PDF de rag-docs/. Autor y año son opcionales (un manual puede no tenerlos)."""
    title: str
    authors: str | None = None
    year: int | None = None

    def label(self, citation_format: str) -> str:
        if not self.authors or self.year is None:
            return self.title
        return citation_format.format(authors=self.authors, year=self.year, title=self.title)


def load_document_sources(path: Path) -> dict[str, DocumentSource]:
    """Fuente de cada archivo. Sin archivo de títulos se usa el nombre del PDF."""
    if not path.is_file():
        return {}
    raw = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(raw, dict) or not all(_is_source(entry) for entry in raw.values()):
        raise ValueError(f"{path.name} must map each PDF file name to its title, authors and year")
    return {file_name: DocumentSource(entry["title"], entry.get("authors"), entry.get("year"))
            for file_name, entry in raw.items()}


def _is_source(entry: object) -> bool:
    return (isinstance(entry, dict) and isinstance(entry.get("title"), str) and entry["title"].strip() != ""
            and isinstance(entry.get("authors"), str | None)
            and (entry.get("year") is None or (isinstance(entry["year"], int) and not isinstance(entry["year"], bool))))


class CitationResolver:
    def __init__(self, catalog: PdfCatalog, sources: dict[str, DocumentSource], citation_format: str):
        # Falla al arrancar si el formato pide un dato que no existe, no en la primera cita.
        DocumentSource("t", "a", 2000).label(citation_format)
        self.catalog = catalog
        self.sources = sources
        self.citation_format = citation_format

    def cite(self, chunk: ScoredChunk) -> Citation | None:
        file_name = chunk.metadata.get(FILE_NAME_KEY)
        page = chunk.metadata.get(PAGE_NUMBER_KEY)
        if not isinstance(file_name, str) or not isinstance(page, int) or isinstance(page, bool):
            return None
        if not self.catalog.contains_page(file_name, page):
            log.warning("Dropping citation to a missing source: file=%s page=%s", file_name, page)
            return None
        source = self.sources.get(file_name)
        title = source.label(self.citation_format) if source else Path(file_name).stem
        return Citation(file_name, page, title)

    def passages(self, chunks: list[ScoredChunk]) -> list[RetrievedPassage]:
        return [RetrievedPassage(chunk.text, self.cite(chunk)) for chunk in chunks]
