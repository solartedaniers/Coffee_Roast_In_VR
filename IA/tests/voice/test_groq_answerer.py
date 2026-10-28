import json

import httpx
import pytest
from pydantic import SecretStr

from app.net.bounded_http import BoundedHttpClient
from app.voice.answer.groq import AnswerUnavailable, GroqAnswerOptions, GroqVoiceAnswerer, ModelAnswer
from app.voice.answer.prompt import AnswerPrompt

BASE_URL = "https://api.groq.com/openai/v1"
KEY = "gsk_test_key"
PROMPT = AnswerPrompt("instrucciones", "pregunta", {})


def answerer_with(handler, api_key=KEY) -> GroqVoiceAnswerer:
    http = BoundedHttpClient([BASE_URL], 100_000, transport=httpx.MockTransport(handler))
    return GroqVoiceAnswerer(http, GroqAnswerOptions(
        base_url=BASE_URL, api_key=SecretStr(api_key) if api_key else None, model="openai/gpt-oss-20b",
        temperature=0.2, max_completion_tokens=700, reasoning_effort="low", connect_timeout_seconds=1,
        read_timeout_seconds=1))


def chat(content):
    return httpx.Response(200, json={"choices": [{"message": {"content": content}}]})


def test_asks_for_the_strict_json_schema_with_system_and_user_messages():
    seen = {}

    def handler(request):
        seen["body"] = json.loads(request.content)
        return chat('{"sufficient": true, "answer": "Sube el gas.", "sourceIds": ["S1", "ROAST"]}')

    answer = answerer_with(handler).answer(PROMPT, 5)

    assert answer == ModelAnswer(sufficient=True, answer="Sube el gas.", source_ids=["S1", "ROAST"])
    body = seen["body"]
    assert body["model"] == "openai/gpt-oss-20b"
    assert body["messages"] == [{"role": "system", "content": "instrucciones"},
                                {"role": "user", "content": "pregunta"}]
    assert body["response_format"]["type"] == "json_schema"
    assert body["response_format"]["json_schema"]["strict"] is True
    assert body["response_format"]["json_schema"]["schema"]["additionalProperties"] is False


def test_does_not_call_groq_without_a_key():
    def handler(request):
        raise AssertionError("Groq should not be called")

    with pytest.raises(AnswerUnavailable, match="key"):
        answerer_with(handler, api_key=None).answer(PROMPT, 5)


@pytest.mark.parametrize("response", [
    httpx.Response(429), httpx.Response(503), chat("no es json"), chat('{"answer": "x"}'), chat(None),
    httpx.Response(200, json={"choices": []}),
], ids=["429", "503", "not-json", "missing-fields", "null", "no-choices"])
def test_reports_an_unusable_answer(response):
    with pytest.raises(AnswerUnavailable):
        answerer_with(lambda request: response).answer(PROMPT, 5)
