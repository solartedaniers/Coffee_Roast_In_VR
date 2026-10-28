from app.rag.embeddings import OllamaEmbeddings


class DocumentEmbedder:
    """Embeddings de los fragmentos a indexar, por lotes."""

    def __init__(self, embeddings: OllamaEmbeddings, batch_size: int):
        self.embeddings = embeddings
        self.batch_size = batch_size

    def embed(self, texts: list[str]) -> list[list[float]]:
        vectors = []
        for start in range(0, len(texts), self.batch_size):
            vectors.extend(self.embeddings.embed(texts[start:start + self.batch_size]))
        return vectors
