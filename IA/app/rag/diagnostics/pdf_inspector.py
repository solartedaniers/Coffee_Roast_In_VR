"""Revisa el texto que se puede extraer de cada PDF de rag-docs/, sin tocar la base."""
import logging
import re
from dataclasses import dataclass
from pathlib import Path

from pypdf import PdfReader

PYPDF_LOGGER = "pypdf"
REPLACEMENT_CHARACTER = "�"
WORD_PATTERN = re.compile(r"[^\W\d_]+")
# Palabras vacías frecuentes: basta para distinguir español de inglés en artículos completos.
STOPWORDS = {
    "es": {"el", "la", "de", "que", "los", "las", "del", "en", "por", "con", "para", "una"},
    "en": {"the", "of", "and", "is", "with", "that", "in", "for", "to", "are", "was", "by"},
}
REFERENCES_HEADING = re.compile(r"(?im)^\s*(references|referencias|bibliograf[ií]a|literatura citada)\b")


@dataclass(frozen=True)
class PdfTextReport:
    file_name: str
    pages: int
    characters: int
    pages_without_text: list[int]
    replacement_characters: int
    extraction_warnings: int
    language: str
    references_start_page: int | None


class _WarningCounter(logging.Handler):
    def __init__(self):
        super().__init__(logging.WARNING)
        self.count = 0

    def emit(self, record: logging.LogRecord) -> None:
        self.count += 1


class PdfTextInspector:
    def __init__(self, min_page_chars: int):
        self.min_page_chars = min_page_chars

    def inspect(self, path: Path) -> PdfTextReport:
        counter = _WarningCounter()
        logger = logging.getLogger(PYPDF_LOGGER)
        logger.addHandler(counter)
        try:
            texts = [page.extract_text() or "" for page in PdfReader(path).pages]
        finally:
            logger.removeHandler(counter)
        full_text = "\n".join(texts)
        return PdfTextReport(
            file_name=path.name,
            pages=len(texts),
            characters=sum(len(text) for text in texts),
            pages_without_text=[number for number, text in enumerate(texts, start=1)
                                if len(text.strip()) < self.min_page_chars],
            replacement_characters=full_text.count(REPLACEMENT_CHARACTER),
            extraction_warnings=counter.count,
            language=detect_language(full_text),
            references_start_page=next((number for number, text in enumerate(texts, start=1)
                                        if REFERENCES_HEADING.search(text)), None),
        )


def detect_language(text: str) -> str:
    words = [word.lower() for word in WORD_PATTERN.findall(text)]
    counts = {language: sum(word in stopwords for word in words) for language, stopwords in STOPWORDS.items()}
    best = max(counts, key=counts.get)
    return best if counts[best] > 0 else "unknown"
