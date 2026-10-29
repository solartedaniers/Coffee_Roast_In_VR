from app.config import SERVICE_ROOT
from app.evaluation.facts import DecisionFactsExtractor
from app.evaluation.prompt import RubricPromptBuilder
from app.evaluation.rubric import CriterionId, Rubric
from tests.support.recorded_sessions import SNAPSHOTS_DIR, read_snapshot
from tests.test_evaluation_facts import trace

RUBRIC = Rubric.from_file(SERVICE_ROOT / "config" / "roast_rubric.json")


def good_prompt():
    return RubricPromptBuilder(RUBRIC).build(DecisionFactsExtractor(RUBRIC).extract(trace()))


def test_the_prompt_matches_the_snapshot_of_the_correct_roast():
    # Si la rúbrica cambia a propósito, regenerar con: python -m tests.test_evaluation_prompt
    prompt = good_prompt()

    assert prompt.system + "\n---\n" + prompt.user == read_snapshot("evaluation-good-prompt.txt")


def test_the_prompt_never_shows_the_rules_score_or_the_result():
    text = good_prompt().user.lower()

    assert "qualityscore" not in text and "perfect" not in text and "dtr" not in text


def test_the_schema_only_asks_for_the_applicable_criteria():
    schema = RubricPromptBuilder(RUBRIC).schema([CriterionId.DRYING_GAS, CriterionId.ALERT_REACTION])

    criteria = schema["properties"]["criteria"]
    assert criteria["required"] == ["DRYING_GAS", "ALERT_REACTION"]
    assert criteria["additionalProperties"] is False
    level = criteria["properties"]["DRYING_GAS"]["properties"]["level"]
    assert level == {"type": "integer", "enum": [0, 1, 2, 3]}


if __name__ == "__main__":
    prompt = good_prompt()
    (SNAPSHOTS_DIR / "evaluation-good-prompt.txt").write_text(prompt.system + "\n---\n" + prompt.user,
                                                              encoding="utf-8", newline="\n")
