from functools import cache
from pathlib import Path

from pypdf import PdfReader


class PdfCatalog:
    """Qué PDFs hay en rag-docs/ y cuántas páginas tiene cada uno."""

    def __init__(self, docs_dir: Path):
        self.docs_dir = docs_dir

    def file_names(self) -> list[str]:
        return sorted(path.name for path in self.docs_dir.glob("*.pdf")) if self.docs_dir.is_dir() else []

    def page_count(self, file_name: str) -> int | None:
        path = self.docs_dir / file_name
        # Solo nombres simples: un nombre con ruta podría apuntar fuera de rag-docs/.
        if Path(file_name).name != file_name or not path.is_file():
            return None
        return _count_pages(path, path.stat().st_mtime_ns)

    def contains_page(self, file_name: str, page_number: int) -> bool:
        pages = self.page_count(file_name)
        return pages is not None and 1 <= page_number <= pages


@cache
def _count_pages(path: Path, modified_ns: int) -> int:
    # modified_ns forma parte de la llave para no usar un conteo viejo si el PDF cambia.
    return len(PdfReader(path).pages)
