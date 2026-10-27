import json

import httpx
import pytest

from app.net.bounded_http import BoundedHttpClient, HttpTimeout
from app.rag.embeddings import EmbeddingError, OllamaEmbeddings

BASE_URL = "http://localhost:11434"


def embeddings_with(handler, dimensions=3) -> OllamaEmbeddings:
    http = BoundedHttpClient([BASE_URL], 1_000_000, transport=httpx.MockTransport(handler))
    return OllamaEmbeddings(http, BASE_URL, "nomic-embed-text", dimensions, HttpTimeout(1, 1))


def test_sends_the_texts_to_the_embed_endpoint():
    seen = {}

    def handler(request):
        seen["url"] = str(request.url)
        seen["body"] = json.loads(request.content)
        return httpx.Response(200, json={"embeddings": [[0.1, 0.2, 0.3], [0.4, 0.5, 0.6]]})

    vectors = embeddings_with(handler).embed(["uno", "dos"])

    assert seen["url"] == "http://localhost:11434/api/embed"
    assert seen["body"] == {"model": "nomic-embed-text", "input": ["uno", "dos"]}
    assert vectors == [[0.1, 0.2, 0.3], [0.4, 0.5, 0.6]]


def test_does_not_call_ollama_without_texts():
    def handler(request):
        raise AssertionError("Ollama should not be called")

    assert embeddings_with(handler).embed([]) == []


@pytest.mark.parametrize("payload", [
    {"embeddings": [[0.1, 0.2]]},
    {"embeddings": [[0.1, 0.2, 0.3], [0.1, 0.2, 0.3]]},
    {"embeddings": [[0.1, 0.2, "x"]]},
    {"error": "model not found"},
])
def test_rejects_embeddings_that_do_not_match_the_index(payload):
    with pytest.raises(EmbeddingError):
        embeddings_with(lambda request: httpx.Response(200, json=payload)).embed(["uno"])


def test_reports_ollama_being_unavailable():
    def handler(request):
        raise httpx.ConnectError("refused")

    with pytest.raises(EmbeddingError):
        embeddings_with(handler).embed_one("uno")
