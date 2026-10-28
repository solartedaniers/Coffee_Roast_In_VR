import json

import pytest

from app.alerts.catalog import AlertCatalog, Priority
from app.alerts.conformance import DEFAULT_CURVES, DEFAULT_OUTPUT, build_vectors, render
from app.alerts.synthetic_curve import CurveSet
from app.config import Settings


@pytest.fixture(scope="module")
def catalog() -> AlertCatalog:
    return AlertCatalog.from_file(Settings(_env_file=None).alerts_catalog_file)


@pytest.fixture(scope="module")
def vectors(catalog) -> dict:
    return json.loads(render(build_vectors(catalog, CurveSet.from_file(DEFAULT_CURVES))))


def alert_ids(vectors: dict, curve: str, level: str) -> list[str]:
    vector = next(v for v in vectors["vectors"] if v["curve"] == curve)
    return [delivery["alertId"] for delivery in vector["expected"][level]]


def test_the_committed_vectors_match_the_catalog_and_the_curves(vectors):
    # Si falla: python -m app.alerts.conformance y revisar el diff antes de subirlo.
    assert json.loads(DEFAULT_OUTPUT.read_text(encoding="utf-8")) == vectors


def test_vectors_carry_the_catalog_version_and_one_line_per_sample(vectors, catalog):
    assert (vectors["schemaVersion"], vectors["catalogRevision"]) == (catalog.schema_version,
                                                                     catalog.catalog_revision)
    good = vectors["vectors"][0]
    assert good["samples"][0] == ["READY", 190.02, 0, False, 8.0, False, False]
    assert len(good["samples"][0]) == len(vectors["sampleFields"])


@pytest.mark.parametrize("level", ["BEGINNER", "INTERMEDIATE", "ADVANCED"])
def test_burned_curve_warns_before_passing_the_ceiling(vectors, level):
    heard = alert_ids(vectors, "burned", level)
    assert heard.index("BURN_WARNING") < heard.index("SECOND_CRACK_NEAR") < heard.index("BURN_CEILING_EXCEEDED")


@pytest.mark.parametrize("curve", ["good", "overdeveloped", "low_charge", "drop", "baked", "raw"])
def test_only_the_burned_curve_hears_burn_alerts(vectors, curve):
    burn = {"BURN_WARNING", "SECOND_CRACK_NEAR", "BURN_CEILING_EXCEEDED", "SCORCH_RISK"}
    assert not burn & set(alert_ids(vectors, curve, "BEGINNER"))


def test_baked_curve_hears_the_maillard_stall_before_the_first_crack(vectors):
    heard = alert_ids(vectors, "baked", "INTERMEDIATE")
    assert heard.index("TEMP_STALL_MAILLARD") < heard.index("FIRST_CRACK")


def test_the_first_crack_is_only_a_subtitle_while_recording(vectors):
    good = vectors["vectors"][0]["expected"]["BEGINNER"]
    assert next(d["mode"] for d in good if d["alertId"] == "FIRST_CRACK") == "SUBTITLE"


def test_low_charge_is_warned_before_loading_the_beans(vectors):
    assert alert_ids(vectors, "low_charge", "ADVANCED")[0] == "CHARGE_TEMP_LOW"


def test_advanced_never_hears_low_priority_alerts(vectors, catalog):
    low = {rule.id for rule in catalog.rules if rule.priority is Priority.LOW}
    for vector in vectors["vectors"]:
        assert not low & {d["alertId"] for d in vector["expected"]["ADVANCED"]}
