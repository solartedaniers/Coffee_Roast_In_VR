"""Limpia el texto que pypdf extrae de un PDF antes de partirlo en fragmentos."""
import re
import unicodedata
from dataclasses import replace

from app.rag.document import Document

# "no -\nticeable", "ve -\ngetativo": palabra cortada al final de la línea. Con guion pegado
# ("energy-\nproducing") es una palabra compuesta y se conserva el guion.
SPLIT_WORD = re.compile(r"(\w) -\s*\n\s*([a-záéíóúñü])")
COMPOUND_LINE_BREAK = re.compile(r"(\w)-\n\s*([a-záéíóúñü])")
DIGITS = re.compile(r"\d+")
REFERENCES_HEADING = re.compile(r"(?im)^\s*(references|referencias|bibliograf[ií]a|literatura citada)\b")
# Nombres de glifo que pypdf deja cuando la fuente no trae su tabla de caracteres: "/gid00030" no
# significa nada y "/C176" es el símbolo de grados ("275 /C176C" = 275 °C). Los demás "/Cnnn" solo
# aparecen en licencias y nombres de programas (®, ©), así que se quitan.
GLYPH_IDS = re.compile(r"(?:/gid\d{5}\s*)+")
DEGREE_GLYPH = "/C176"
OTHER_CHAR_GLYPHS = re.compile(r"/C\d{3}")


class PdfTextCleaner:
    """Une palabras cortadas, quita encabezados y pies repetidos y descarta la bibliografía.

    Una línea es encabezado si, sin sus números, aparece en al menos `repeated_line_page_ratio`
    de las páginas del mismo PDF (por ejemplo "Cenicafé, 73(1) 2022" o el título del manual).
    """

    def __init__(self, repeated_line_page_ratio: float):
        self.repeated_line_page_ratio = repeated_line_page_ratio

    def clean_pages(self, pages: list[Document]) -> list[Document]:
        texts = [self._join_split_words(_drop_glyph_names(unicodedata.normalize("NFKC", page.text))) for page in pages]
        texts = self._drop_references(texts)
        repeated = self._repeated_lines(texts)
        return [replace(page, text="\n".join(line for line in text.splitlines()
                                             if _line_key(line) not in repeated))
                for page, text in zip(pages, texts, strict=True)]

    @staticmethod
    def _join_split_words(text: str) -> str:
        return COMPOUND_LINE_BREAK.sub(r"\1-\2", SPLIT_WORD.sub(r"\1\2", text))

    @staticmethod
    def _drop_references(texts: list[str]) -> list[str]:
        """Corta en el título de la bibliografía y vacía las páginas siguientes.

        Solo se busca en la segunda mitad del PDF: un índice al inicio que diga "Bibliografía"
        no debe vaciar el documento.
        """
        for index in range(len(texts) // 2, len(texts)):
            text = texts[index]
            heading = REFERENCES_HEADING.search(text)
            if heading:
                return texts[:index] + [text[:heading.start()]] + [""] * (len(texts) - index - 1)
        return texts

    def _repeated_lines(self, texts: list[str]) -> set[str]:
        pages_with_line: dict[str, int] = {}
        for text in texts:
            for key in {_line_key(line) for line in text.splitlines()} - {""}:
                pages_with_line[key] = pages_with_line.get(key, 0) + 1
        minimum = max(2, self.repeated_line_page_ratio * len(texts))
        # Las líneas que eran solo números (páginas) quedan con llave vacía y se quitan siempre.
        return {key for key, count in pages_with_line.items() if count >= minimum} | {""}


def _drop_glyph_names(text: str) -> str:
    return OTHER_CHAR_GLYPHS.sub("", GLYPH_IDS.sub(" ", text).replace(DEGREE_GLYPH, "°"))


def _line_key(line: str) -> str:
    return " ".join(DIGITS.sub("", line).split()).casefold()
