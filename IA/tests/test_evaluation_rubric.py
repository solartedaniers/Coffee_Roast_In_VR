import json

import pytest
from pydantic import ValidationError

from app.config import SERVICE_ROOT
from app.evaluation.justification_guard import JustificationGuard
from app.evaluation.rubric import CriterionId, Evidence, Rubric, RubricStatus

RUBRIC_FILE = SERVICE_ROOT / "config" / "roast_rubric.json"


def raw_rubric() -> dict:
    return json.loads(RUBRIC_FILE.read_text(encoding="utf-8"))


def test_the_shipped_rubric_is_a_draft_with_the_approved_criteria():
    rubric = Rubric.from_file(RUBRIC_FILE)

    assert rubric.status is RubricStatus.DRAFT
    assert [criterion.id for criterion in rubric.criteria] == [
        CriterionId.DRYING_GAS, CriterionId.RISE_TO_FIRST_CRACK,
        CriterionId.GAS_AFTER_FIRST_CRACK, CriterionId.ALERT_REACTION]
    assert rubric.criterion(CriterionId.GAS_AFTER_FIRST_CRACK).evidence is Evidence.SOURCED
    assert "Münchow et al. (2020)" in rubric.criterion(CriterionId.GAS_AFTER_FIRST_CRACK).source


def test_renders_parameters_without_trailing_zeros():
    rubric = Rubric.from_file(RUBRIC_FILE)

    text = rubric.render(rubric.criterion(CriterionId.DRYING_GAS).description)

    assert "160 °C" in text and "5 a 25 °C/min" in text


def test_rejects_an_unknown_placeholder():
    data = raw_rubric()
    data["criteria"][0]["levels"]["3"] = "Usa {unknownParameter}."

    with pytest.raises(ValidationError, match="placeholder"):
        Rubric.model_validate(data)


def test_rejects_missing_levels():
    data = raw_rubric()
    del data["criteria"][1]["levels"]["0"]

    with pytest.raises(ValidationError, match="levels"):
        Rubric.model_validate(data)


def test_rejects_repeated_criteria():
    data = raw_rubric()
    data["criteria"].append(data["criteria"][0])

    with pytest.raises(ValidationError, match="repeat"):
        Rubric.model_validate(data)


def test_rejects_ranges_out_of_order():
    data = raw_rubric()
    data["parameters"]["dryingRiseMin"] = 30

    with pytest.raises(ValidationError, match="order"):
        Rubric.model_validate(data)


def test_rejects_an_unknown_reaction():
    data = raw_rubric()
    data["alertReactions"]["BURN_WARNING"] = "PRAY"

    with pytest.raises(ValidationError):
        Rubric.model_validate(data)


def test_every_fallback_justification_is_plain_text_for_the_player():
    rubric = Rubric.from_file(RUBRIC_FILE)
    guard = JustificationGuard(rubric)

    for criterion in rubric.criteria:
        for text in criterion.fallback_justifications.values():
            rendered = rubric.render(text)
            assert guard.review(rendered).text == rendered, rendered
            assert len(rendered) <= rubric.parameters.justification_max_chars


def test_rejects_missing_fallback_justifications():
    data = raw_rubric()
    del data["criteria"][2]["fallbackJustifications"]["3"]

    with pytest.raises(ValidationError, match="fallback"):
        Rubric.model_validate(data)
