import json

import httpx
import pytest
from pydantic import SecretStr

from app.feedback.providers.base import ProviderUnavailable
from app.feedback.providers.groq import GroqFeedbackProvider, GroqOptions
from app.net.bounded_http import BoundedHttpClient

BASE_URL = "https://api.groq.com/openai/v1"
KEY = "gsk_test_key"


def provider_with(handler, settings, api_key=KEY) -> GroqFeedbackProvider:
    http = BoundedHttpClient([BASE_URL], 1_000_000, transport=httpx.MockTransport(handler))
    return GroqFeedbackProvider(http, GroqOptions(
        base_url=BASE_URL, api_key=SecretStr(api_key) if api_key else None, model=settings.groq_model,
        temperature=settings.groq_temperature, max_completion_tokens=settings.groq_max_completion_tokens,
        reasoning_effort=settings.groq_reasoning_effort, connect_timeout_seconds=1, read_timeout_seconds=1))


def chat_response(content):
    return httpx.Response(200, json={"choices": [{"message": {"role": "assistant", "content": content}}]})


def test_sends_the_whole_prompt_as_one_user_message(settings):
    seen = {}

    def handler(request):
        seen["url"] = str(request.url)
        seen["auth"] = request.headers["authorization"]
        seen["body"] = json.loads(request.content)
        return chat_response("Buen tueste.")

    text = provider_with(handler, settings).generate("El prompt", 10)

    assert text == "Buen tueste."
    assert seen["url"] == "https://api.groq.com/openai/v1/chat/completions"
    assert seen["auth"] == f"Bearer {KEY}"
    assert seen["body"] == {
        "model": "openai/gpt-oss-120b", "messages": [{"role": "user", "content": "El prompt"}],
        "temperature": 0.4, "max_completion_tokens": 1024, "reasoning_effort": "low", "include_reasoning": False,
    }


def test_does_not_call_groq_without_a_key(settings):
    def handler(request):
        raise AssertionError("Groq should not be called")

    with pytest.raises(ProviderUnavailable, match="key"):
        provider_with(handler, settings, api_key=None).generate("El prompt", 10)


@pytest.mark.parametrize("response", [
    httpx.Response(429, json={"error": {"message": "rate limit"}}),
    httpx.Response(401, json={"error": {"message": "invalid key"}}),
    httpx.Response(200, json={"choices": []}),
    chat_response(""),
    chat_response(None),
])
def test_reports_groq_being_unavailable(settings, response):
    with pytest.raises(ProviderUnavailable):
        provider_with(lambda request: response, settings).generate("El prompt", 10)


def test_never_puts_the_key_in_the_error(settings):
    with pytest.raises(ProviderUnavailable) as error:
        provider_with(lambda request: httpx.Response(401, text=KEY), settings).generate("El prompt", 10)

    assert KEY not in str(error.value)
