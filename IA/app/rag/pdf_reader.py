from pathlib import Path

from pypdf import PdfReader

from app.rag.document import Document

FILE_NAME_KEY = "file_name"
PAGE_NUMBER_KEY = "page_number"


class PdfPageReader:
    """Un documento por página, con el nombre del archivo y el número de página (desde 1)."""

    def read(self, path: Path) -> list[Document]:
        reader = PdfReader(path)
        return [Document(page.extract_text() or "", {PAGE_NUMBER_KEY: number, FILE_NAME_KEY: path.name})
                for number, page in enumerate(reader.pages, start=1)]
