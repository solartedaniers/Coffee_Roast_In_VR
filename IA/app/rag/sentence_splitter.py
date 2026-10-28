import re
import uuid

from app.rag.document import Document
from app.rag.pdf_text_cleaner import PdfTextCleaner
from app.rag.text_splitter import (
    CHUNK_INDEX_KEY,
    PARENT_DOCUMENT_ID_KEY,
    TOTAL_CHUNKS_KEY,
    CharacterTextSplitter,
    TextSplitter,
)

# Fin de frase: punto, signo de cierre o dos puntos seguidos de espacio y mayúscula, número o "¿¡•".
SENTENCE_END = re.compile(r"(?<=[.!?:;])\s+(?=[A-ZÁÉÍÓÚÑ¿¡•\d(])")


class SentenceTextSplitter:
    """Junta frases completas hasta `chunk_size_chars`, sin cruzar de una página a otra.

    El solapamiento son las últimas frases del fragmento anterior que caben en
    `chunk_overlap_chars`. Una frase más larga que el fragmento se corta por caracteres, y los
    fragmentos más cortos que `min_chunk_chars` (restos de tablas o pies) se descartan.
    """

    def __init__(self, chunk_size_chars: int, chunk_overlap_chars: int, min_chunk_chars: int):
        self.chunk_size_chars = chunk_size_chars
        self.chunk_overlap_chars = chunk_overlap_chars
        self.min_chunk_chars = min_chunk_chars
        self.long_sentence_splitter = CharacterTextSplitter(chunk_size_chars, chunk_overlap_chars)

    def split_documents(self, documents: list[Document]) -> list[Document]:
        result = []
        for document in documents:
            chunks = self.split_text(document.text)
            parent_id = str(uuid.uuid4())
            for index, chunk in enumerate(chunks):
                result.append(Document(chunk, dict(document.metadata) | {
                    PARENT_DOCUMENT_ID_KEY: parent_id, CHUNK_INDEX_KEY: index, TOTAL_CHUNKS_KEY: len(chunks),
                }))
        return result

    def split_text(self, text: str) -> list[str]:
        chunks: list[str] = []
        current: list[str] = []
        for sentence in self._sentences(text):
            if current and len(" ".join(current + [sentence])) > self.chunk_size_chars:
                chunks.append(" ".join(current))
                current = self._overlap(current)
            current.append(sentence)
        if current:
            chunks.append(" ".join(current))
        return [chunk for chunk in chunks if len(chunk) >= self.min_chunk_chars]

    def _sentences(self, text: str) -> list[str]:
        flat = " ".join(text.split())
        sentences = []
        for sentence in SENTENCE_END.split(flat):
            if len(sentence) > self.chunk_size_chars:
                sentences.extend(self.long_sentence_splitter.split_text(sentence))
            elif sentence:
                sentences.append(sentence)
        return sentences

    def _overlap(self, sentences: list[str]) -> list[str]:
        kept: list[str] = []
        for sentence in reversed(sentences):
            if len(" ".join([sentence] + kept)) > self.chunk_overlap_chars:
                break
            kept.insert(0, sentence)
        return kept


class CleaningTextSplitter:
    """Limpia las páginas de un PDF y después las parte."""

    def __init__(self, cleaner: PdfTextCleaner, splitter: TextSplitter):
        self.cleaner = cleaner
        self.splitter = splitter

    def split_documents(self, documents: list[Document]) -> list[Document]:
        return self.splitter.split_documents(self.cleaner.clean_pages(documents))
