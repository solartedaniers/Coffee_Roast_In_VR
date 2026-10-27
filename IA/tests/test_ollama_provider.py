import json

import httpx
import pytest

from app.feedback.providers.base import ProviderUnavailable
from app.feedback.providers.ollama import OllamaFeedbackProvider, OllamaOptions
from app.net.bounded_http import BoundedHttpClient

BASE_URL = "http://localhost:11434"


def provider_with(handler, settings) -> OllamaFeedbackProvider:
    http = BoundedHttpClient([BASE_URL], 1_000_000, transport=httpx.MockTransport(handler))
    return OllamaFeedbackProvider(http, OllamaOptions(
        base_url=BASE_URL, model=settings.ollama_feedback_model, keep_alive=settings.ollama_keep_alive,
        temperature=settings.ollama_temperature, num_predict=settings.ollama_num_predict,
        connect_timeout_seconds=1, read_timeout_seconds=1))


def test_sends_the_prompt_with_the_same_generation_options_as_java(settings):
    seen = {}

    def handler(request):
        seen["url"] = str(request.url)
        seen["body"] = json.loads(request.content)
        return httpx.Response(200, json={"response": "Buen tueste."})

    text = provider_with(handler, settings).generate("El prompt", 10)

    assert text == "Buen tueste."
    assert seen["url"] == "http://localhost:11434/api/generate"
    assert seen["body"] == {
        "model": "phi4-mini", "prompt": "El prompt", "stream": False, "keep_alive": "10m", "stop": ["\n\n"],
        "options": {"temperature": 0.4, "num_predict": 240},
    }


@pytest.mark.parametrize("response", [
    httpx.Response(500, json={"error": "boom"}),
    httpx.Response(200, json={"error": "model not found"}),
    httpx.Response(200, json=["unexpected"]),
])
def test_reports_ollama_being_unavailable(settings, response):
    with pytest.raises(ProviderUnavailable):
        provider_with(lambda request: response, settings).generate("El prompt", 10)


def test_reports_a_connection_failure(settings):
    def handler(request):
        raise httpx.ConnectError("refused")

    with pytest.raises(ProviderUnavailable):
        provider_with(handler, settings).generate("El prompt", 10)
