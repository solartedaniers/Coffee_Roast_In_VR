import pytest

from app.alerts.catalog import AlertCatalog, RoastPhase
from app.alerts.evaluator import AlertEvaluator, DeliveryMode
from app.alerts.signals import RoastSample
from app.config import Settings
from app.domain import KnowledgeLevel


@pytest.fixture(scope="module")
def catalog() -> AlertCatalog:
    return AlertCatalog.from_file(Settings(_env_file=None).alerts_catalog_file)


def ready(seconds: int, temperature: float) -> list[RoastSample]:
    return [RoastSample(RoastPhase.READY, temperature, 0, False) for _ in range(seconds)]


def roasting(start: int, temperatures, first_crack=False) -> list[RoastSample]:
    return [RoastSample(RoastPhase.ROASTING, t, start + i, first_crack) for i, t in enumerate(temperatures)]


def test_warns_about_a_low_charge_with_the_phrase_of_the_level(catalog):
    evaluator = AlertEvaluator(catalog, KnowledgeLevel.ADVANCED)
    deliveries = [evaluator.step(sample) for sample in ready(5, 170)]
    fired = [d for d in deliveries if d]
    assert [(d.second, d.alert_id, d.mode) for d in fired] == [(2, "CHARGE_TEMP_LOW", DeliveryMode.VOICE)]
    assert fired[0].text == "Carga por debajo de 180 grados."


def test_only_subtitles_while_the_player_records_a_question(catalog):
    evaluator = AlertEvaluator(catalog, KnowledgeLevel.BEGINNER)
    deliveries = [evaluator.step(sample, recording=True) for sample in ready(5, 210)]
    assert [d.mode for d in deliveries if d] == [DeliveryMode.SUBTITLE]


def test_advanced_hears_the_first_crack_but_not_the_soft_guidance(catalog):
    samples = roasting(500, [185, 190] + [194] * 8) + roasting(510, [197, 198], first_crack=True)
    heard = {}
    for level in (KnowledgeLevel.BEGINNER, KnowledgeLevel.ADVANCED):
        evaluator = AlertEvaluator(catalog, level)
        heard[level] = [d.alert_id for d in map(evaluator.step, samples) if d]
    assert heard[KnowledgeLevel.BEGINNER] == ["FIRST_CRACK_NEAR", "FIRST_CRACK"]
    assert heard[KnowledgeLevel.ADVANCED] == ["FIRST_CRACK"]


def test_the_ceiling_alert_interrupts_the_burn_warning(catalog):
    evaluator = AlertEvaluator(catalog, KnowledgeLevel.INTERMEDIATE)
    samples = roasting(600, [205] * 20 + [212, 215, 216, 217, 218, 219, 220, 221], first_crack=True)
    fired = [(d.roast_seconds, d.alert_id) for d in map(evaluator.step, samples) if d]
    assert fired == [(600, "FIRST_CRACK"), (621, "BURN_WARNING"), (624, "SECOND_CRACK_NEAR"),
                     (627, "BURN_CEILING_EXCEEDED")]
