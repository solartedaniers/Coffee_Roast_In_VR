from app.rag.embeddings import OllamaEmbeddings


class DocumentEmbedder:
    """Embeddings de los fragmentos a indexar, por lotes.

    `document_prefix` es la instrucción de tarea del modelo (nomic-embed-text usa
    "search_document: "); solo se agrega al calcular el embedding, el texto guardado no cambia.
    """

    def __init__(self, embeddings: OllamaEmbeddings, batch_size: int, document_prefix: str = ""):
        self.embeddings = embeddings
        self.batch_size = batch_size
        self.document_prefix = document_prefix

    def embed(self, texts: list[str]) -> list[list[float]]:
        vectors = []
        for start in range(0, len(texts), self.batch_size):
            batch = texts[start:start + self.batch_size]
            vectors.extend(self.embeddings.embed([self.document_prefix + text for text in batch]))
        return vectors
