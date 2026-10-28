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


def load_document_titles(path: Path) -> dict[str, str]:
    """Títulos legibles por archivo. Sin archivo de títulos se usa el nombre del PDF."""
    if not path.is_file():
        return {}
    titles = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(titles, dict) or not all(isinstance(v, str) for v in titles.values()):
        raise ValueError(f"{path.name} must map each PDF file name to a title")
    return titles


class CitationResolver:
    def __init__(self, catalog: PdfCatalog, titles: dict[str, str]):
        self.catalog = catalog
        self.titles = titles

    def cite(self, chunk: ScoredChunk) -> Citation | None:
        file_name = chunk.metadata.get(FILE_NAME_KEY)
        page = chunk.metadata.get(PAGE_NUMBER_KEY)
        if not isinstance(file_name, str) or not isinstance(page, int) or isinstance(page, bool):
            return None
        if not self.catalog.contains_page(file_name, page):
            log.warning("Dropping citation to a missing source: file=%s page=%s", file_name, page)
            return None
        return Citation(file_name, page, self.titles.get(file_name, Path(file_name).stem))

    def passages(self, chunks: list[ScoredChunk]) -> list[RetrievedPassage]:
        return [RetrievedPassage(chunk.text, self.cite(chunk)) for chunk in chunks]
