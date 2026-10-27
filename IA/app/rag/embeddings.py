import math

from app.net.bounded_http import BoundedHttpClient, HttpTimeout, RemoteServiceError

EMBED_ENDPOINT = "/api/embed"


class EmbeddingError(Exception):
    """No se pudieron calcular los embeddings."""


class OllamaEmbeddings:
    """Embeddings de Ollama con el mismo modelo con que se indexó rag_document_chunks."""

    def __init__(self, http: BoundedHttpClient, base_url: str, model: str, dimensions: int, timeout: HttpTimeout):
        self.http = http
        self.url = base_url.rstrip("/") + EMBED_ENDPOINT
        self.model = model
        self.dimensions = dimensions
        self.timeout = timeout

    def embed(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            return []
        try:
            result = self.http.post_json(self.url, {"model": self.model, "input": texts}, {}, self.timeout)
        except RemoteServiceError as error:
            raise EmbeddingError(str(error)) from error
        vectors = result.get("embeddings") if isinstance(result, dict) else None
        if not isinstance(vectors, list) or len(vectors) != len(texts):
            raise EmbeddingError("Embedding count differs from the input")
        for vector in vectors:
            # Una dimensión distinta corrompería la búsqueda sobre el índice existente.
            if (not isinstance(vector, list) or len(vector) != self.dimensions
                    or not all(isinstance(number, (int, float)) and math.isfinite(number) for number in vector)):
                raise EmbeddingError("Embedding does not match the index dimensions")
        return vectors

    def embed_one(self, text: str) -> list[float]:
        return self.embed([text])[0]
