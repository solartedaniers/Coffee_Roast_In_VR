import uuid

from app.java_format import java_strip
from app.rag.document import Document

# Metadatos de posición que Spring AI agregaba a cada fragmento.
PARENT_DOCUMENT_ID_KEY = "parent_document_id"
CHUNK_INDEX_KEY = "chunk_index"
TOTAL_CHUNKS_KEY = "total_chunks"

class CharacterTextSplitter:
    """Ventana deslizante por caracteres, con solapamiento, igual a la que usaba el servicio en Java."""

    def __init__(self, chunk_size_chars: int, chunk_overlap_chars: int):
        if chunk_overlap_chars >= chunk_size_chars:
            raise ValueError("The chunk overlap must be smaller than the chunk size")
        self.chunk_size_chars = chunk_size_chars
        self.chunk_overlap_chars = chunk_overlap_chars

    def split_documents(self, documents: list[Document]) -> list[Document]:
        result = []
        for document in documents:
            chunks = self.split_text(document.text)
            parent_id = str(uuid.uuid4())
            for index, chunk in enumerate(chunks):
                metadata = dict(document.metadata) | {
                    PARENT_DOCUMENT_ID_KEY: parent_id, CHUNK_INDEX_KEY: index, TOTAL_CHUNKS_KEY: len(chunks),
                }
                result.append(Document(chunk, metadata))
        return result

    def split_text(self, text: str) -> list[str]:
        chunks = []
        step = self.chunk_size_chars - self.chunk_overlap_chars
        for start in range(0, len(text), step):
            end = min(start + self.chunk_size_chars, len(text))
            chunk = java_strip(text[start:end])
            if chunk:
                chunks.append(chunk)
            if end == len(text):
                break
        return chunks
