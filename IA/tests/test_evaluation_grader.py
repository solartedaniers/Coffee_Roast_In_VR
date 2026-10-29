import json

import httpx
import pytest
from pydantic import SecretStr

from app.evaluation.grader import CriterionGrade, GradingUnavailable, GroqGraderOptions, GroqRubricGrader
from app.evaluation.prompt import RubricPrompt, RubricPromptBuilder
from app.evaluation.rubric import CriterionId
from app.net.bounded_http import BoundedHttpClient
from tests.test_evaluation_prompt import RUBRIC

BASE_URL = "https://api.groq.com/openai/v1"
IDS = [CriterionId.DRYING_GAS, CriterionId.ALERT_REACTION]
PROMPT = RubricPrompt("instrucciones", "criterios", RubricPromptBuilder(RUBRIC).schema(IDS))


def grader_with(handler, api_key="gsk_test_key") -> GroqRubricGrader:
    http = BoundedHttpClient([BASE_URL], 100_000, transport=httpx.MockTransport(handler))
    return GroqRubricGrader(http, GroqGraderOptions(
        base_url=BASE_URL, api_key=SecretStr(api_key) if api_key else None, model="openai/gpt-oss-120b",
        temperature=0, seed=7, max_completion_tokens=2048, reasoning_effort="low", connect_timeout_seconds=1,
        read_timeout_seconds=1))


def chat(criteria):
    return httpx.Response(200, json={"choices": [{"message": {"content": json.dumps({"criteria": criteria})}}]})


GOOD = {"DRYING_GAS": {"justification": " Mantuviste la subida en franja. ", "level": 3},
        "ALERT_REACTION": {"justification": "Reaccionaste a 1 de 2 alertas.", "level": 1}}


def test_asks_for_a_deterministic_strict_grade_and_reads_it():
    seen = {}

    def handler(request):
        seen["body"] = json.loads(request.content)
        return chat(GOOD)

    grades = grader_with(handler).grade(PROMPT)

    assert grades == {CriterionId.DRYING_GAS: CriterionGrade(3, "Mantuviste la subida en franja."),
                      CriterionId.ALERT_REACTION: CriterionGrade(1, "Reaccionaste a 1 de 2 alertas.")}
    body = seen["body"]
    assert body["model"] == "openai/gpt-oss-120b" and body["temperature"] == 0 and body["seed"] == 7
    assert body["messages"][0] == {"role": "system", "content": "instrucciones"}
    assert body["response_format"]["json_schema"]["strict"] is True
    assert body["response_format"]["json_schema"]["schema"] == PROMPT.schema


def test_does_not_call_groq_without_a_key():
    def handler(request):
        raise AssertionError("Groq should not be called")

    with pytest.raises(GradingUnavailable, match="key"):
        grader_with(handler, api_key=None).grade(PROMPT)


@pytest.mark.parametrize("response", [
    httpx.Response(429), httpx.Response(503),
    httpx.Response(200, json={"choices": [{"message": {"content": "no es json"}}]}),
    chat({"DRYING_GAS": GOOD["DRYING_GAS"]}),
    chat(GOOD | {"RISE_TO_FIRST_CRACK": GOOD["DRYING_GAS"]}),
    chat(GOOD | {"DRYING_GAS": {"justification": "x", "level": 4}}),
    chat(GOOD | {"DRYING_GAS": {"justification": "x", "level": True}}),
    chat(GOOD | {"DRYING_GAS": {"justification": "x", "level": 2.0}}),
    chat(GOOD | {"DRYING_GAS": {"justification": "  ", "level": 2}}),
], ids=["429", "503", "not-json", "missing", "extra", "level-high", "level-bool", "level-float", "empty"])
def test_reports_an_unusable_grade(response):
    with pytest.raises(GradingUnavailable):
        grader_with(lambda request: response).grade(PROMPT)


REWRITE = RubricPrompt("instrucciones", "criterios", RubricPromptBuilder(RUBRIC).rewrite_schema(IDS))


def test_reads_a_rewrite_with_only_the_justifications():
    seen = {}

    def handler(request):
        seen["body"] = json.loads(request.content)
        return chat({"DRYING_GAS": {"justification": " Subiste a buen ritmo. "},
                     "ALERT_REACTION": {"justification": "Reaccionaste a una de dos alertas."}})

    texts = grader_with(handler).rewrite(REWRITE)

    assert texts == {CriterionId.DRYING_GAS: "Subiste a buen ritmo.",
                     CriterionId.ALERT_REACTION: "Reaccionaste a una de dos alertas."}
    assert seen["body"]["response_format"]["json_schema"]["schema"] == REWRITE.schema


@pytest.mark.parametrize("criteria", [
    {"DRYING_GAS": {"justification": "Bien."}},
    {"DRYING_GAS": {"justification": "Bien."}, "ALERT_REACTION": {"justification": "  "}},
    {"DRYING_GAS": {"justification": "Bien."}, "ALERT_REACTION": {"text": "Bien."}},
], ids=["missing", "empty", "wrong-field"])
def test_reports_an_unusable_rewrite(criteria):
    with pytest.raises(GradingUnavailable):
        grader_with(lambda request: chat(criteria)).rewrite(REWRITE)
