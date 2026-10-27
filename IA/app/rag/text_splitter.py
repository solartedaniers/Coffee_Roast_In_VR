from app.rag.document import Document

# Caracteres que String.strip() de Java considera espacio. A diferencia de Python, Java no
# quita los espacios de no separación (U+00A0, U+2007, U+202F) ni U+0085.
_JAVA_NON_WHITESPACE = {" ", " ", " ", "\u0085"}


def java_strip(value: str) -> str:
    start, end = 0, len(value)
    while start < end and value[start].isspace() and value[start] not in _JAVA_NON_WHITESPACE:
        start += 1
    while end > start and value[end - 1].isspace() and value[end - 1] not in _JAVA_NON_WHITESPACE:
        end -= 1
    return value[start:end]


class CharacterTextSplitter:
    """Ventana deslizante por caracteres, con solapamiento, igual a la que usaba el servicio en Java."""

    def __init__(self, chunk_size_chars: int, chunk_overlap_chars: int):
        if chunk_overlap_chars >= chunk_size_chars:
            raise ValueError("The chunk overlap must be smaller than the chunk size")
        self.chunk_size_chars = chunk_size_chars
        self.chunk_overlap_chars = chunk_overlap_chars

    def split_documents(self, documents: list[Document]) -> list[Document]:
        return [Document(chunk, dict(document.metadata))
                for document in documents
                for chunk in self.split_text(document.text)]

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
