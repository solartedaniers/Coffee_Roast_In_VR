import copy
import json

import pytest
from pydantic import ValidationError

from app.alerts.catalog import AlertCatalog, Priority, Signal
from app.config import Settings
from app.domain import KnowledgeLevel

GUIDANCE_RULES = {"DRYING_RISE_FAST", "DRYING_RISE_SLOW", "FIRST_CRACK_NEAR"}

MINIMAL_CATALOG = {
    "schemaVersion": 1,
    "catalogRevision": 1,
    "defaultLevel": "INTERMEDIATE",
    "signals": {"riseWindowSeconds": 30, "maillardStartTemperature": 160, "maillardEndTemperature": 200,
                "burnCeilingTemperature": 220, "turningPointMinRise": 0.5},
    "channel": {"queueMaxWaitSeconds": 5, "minGapSeconds": 8, "urgentPriorities": ["CRITICAL"],
                "unqueuedPriorities": ["LOW"]},
    "rules": [{
        "id": "FIRST_CRACK", "priority": "HIGH", "levels": ["BEGINNER"],
        "when": [{"signal": "firstCrackReached", "op": "eq", "value": True}],
        "sustainSeconds": 1, "rearmWhen": None, "cooldownSeconds": 0, "maxRepeats": 1,
        "requiresQuietSeconds": 0, "source": "Propuesto.", "phrases": {"BEGINNER": "Primer crack."},
    }],
}


def catalog_with(**rule_changes) -> dict:
    data = copy.deepcopy(MINIMAL_CATALOG)
    data["rules"][0].update(rule_changes)
    return data


@pytest.fixture(scope="module")
def catalog(settings_module) -> AlertCatalog:
    return AlertCatalog.from_file(settings_module.alerts_catalog_file)


@pytest.fixture(scope="module")
def settings_module() -> Settings:
    return Settings(_env_file=None)


def test_loads_the_catalog_of_the_repository(catalog):
    assert catalog.schema_version == 1
    assert catalog.default_level is KnowledgeLevel.INTERMEDIATE
    assert len(catalog.rules) == 17


def test_advanced_gets_no_encouragement_nor_soft_guidance(catalog):
    advanced = {rule.id for rule in catalog.rules_for(KnowledgeLevel.ADVANCED)}
    low = {rule.id for rule in catalog.rules if rule.priority is Priority.LOW}
    assert not advanced & (low | GUIDANCE_RULES)
    assert {rule.id for rule in catalog.rules_for(KnowledgeLevel.BEGINNER)} == {rule.id for rule in catalog.rules}


def test_development_alerts_use_the_same_ideal_range_as_the_rag(catalog, settings_module):
    thresholds = {rule.id: condition.value for rule in catalog.rules for condition in rule.when
                  if condition.signal is Signal.DEVELOPMENT_RATIO}
    assert thresholds == {"DEVELOPMENT_READY": settings_module.rag_dtr_optimal_min,
                          "DISCHARGE_WINDOW_CLOSING": settings_module.rag_dtr_optimal_max}


def test_burn_alerts_follow_the_current_roast_rules(catalog):
    temperatures = {rule.id: [c.value for c in rule.when if c.signal is Signal.TEMPERATURE]
                    for rule in catalog.rules if rule.id in {"BURN_WARNING", "SECOND_CRACK_NEAR"}}
    assert temperatures == {"BURN_WARNING": [215], "SECOND_CRACK_NEAR": [218]}
    assert catalog.signals.burn_ceiling_temperature == 220


def test_maillard_stall_only_counts_before_the_first_crack(catalog):
    rule = next(rule for rule in catalog.rules if rule.id == "TEMP_STALL_MAILLARD")
    assert any(c.signal is Signal.FIRST_CRACK_REACHED and c.value is False for c in rule.when)


def test_drying_alerts_wait_for_the_turning_point(catalog):
    for rule_id in ("TEMP_STALL_DRYING", "DRYING_RISE_SLOW", "DRYING_RISE_FAST"):
        rule = next(rule for rule in catalog.rules if rule.id == rule_id)
        assert any(c.signal is Signal.TURNING_POINT_REACHED and c.value is True for c in rule.when)


def test_accepts_the_minimal_catalog():
    catalog = AlertCatalog.model_validate(MINIMAL_CATALOG)
    assert catalog.rules_for(KnowledgeLevel.ADVANCED) == ()


@pytest.mark.parametrize("condition", [
    {"signal": "unknown", "op": "eq", "value": 1},
    {"signal": "temperature", "op": "between", "value": 1},
    {"signal": "temperature", "op": "ge", "value": True},
    {"signal": "temperature", "op": "ge", "value": "200"},
    {"signal": "firstCrackReached", "op": "lt", "value": True},
    {"signal": "firstCrackReached", "op": "eq", "value": 1},
    {"signal": "phase", "op": "eq", "value": "FINISHED"},
    {"signal": "phase", "op": "ge", "value": "READY"},
])
def test_rejects_invalid_conditions(condition):
    with pytest.raises(ValidationError):
        AlertCatalog.model_validate(catalog_with(when=[condition]))


@pytest.mark.parametrize("changes", [
    {"phrases": {}},
    {"phrases": {"BEGINNER": "  "}},
    {"phrases": {"BEGINNER": "Primer crack.", "ADVANCED": "Primer crack."}},
    {"levels": []},
    {"levels": ["BEGINNER", "BEGINNER"]},
    {"when": []},
    {"sustainSeconds": 0},
    {"maxRepeats": 0},
    {"cooldownSeconds": -1},
    {"priority": "URGENT"},
    {"unexpected": True},
])
def test_rejects_invalid_rules(changes):
    with pytest.raises(ValidationError):
        AlertCatalog.model_validate(catalog_with(**changes))


def test_rejects_repeated_ids_and_unsupported_versions():
    repeated = copy.deepcopy(MINIMAL_CATALOG)
    repeated["rules"].append(copy.deepcopy(repeated["rules"][0]))
    future = copy.deepcopy(MINIMAL_CATALOG) | {"schemaVersion": 2}
    inverted = copy.deepcopy(MINIMAL_CATALOG)
    inverted["signals"]["maillardStartTemperature"] = 200
    for data in (repeated, future, inverted):
        with pytest.raises(ValidationError):
            AlertCatalog.model_validate(data)


def test_reads_the_catalog_from_a_file(tmp_path):
    path = tmp_path / "alerts.json"
    path.write_text(json.dumps(MINIMAL_CATALOG), encoding="utf-8")
    assert AlertCatalog.from_file(path).rules[0].id == "FIRST_CRACK"
