import pytest

from app.config import SERVICE_ROOT
from app.evaluation.facts import DecisionFactsExtractor
from app.evaluation.rubric import CriterionId, Rubric
from app.evaluation.synthetic_trace import SyntheticTrace

RUBRIC = Rubric.from_file(SERVICE_ROOT / "config" / "roast_rubric.json")

# Curva "good" de eval/alerts/curves.json en segundos del tueste: primer crack a los 604 s y DTR 20 %.
GOOD_POINTS = [(0, 108.59), (9, 110.62), (50, 120.88), (67, 124.78), (87, 128.77), (117, 133.84), (152, 138.88),
               (300, 158.12), (333, 162.74), (359, 167.46), (409, 175.7), (465, 183.81), (497, 187.97),
               (531, 192.05), (568, 196.12), (610, 200.25), (629, 201.11), (756, 204.81)]


def trace(**changes):
    values = {"id": "case", "description": "", "duration_seconds": 756, "points": GOOD_POINTS,
              "initial_gas": 30, "gas_changes": [(604, 25)], "first_crack_second": 604} | changes
    return SyntheticTrace(**values).trace()


def facts_of(**changes):
    return DecisionFactsExtractor(RUBRIC).extract(trace(**changes))


def test_the_correct_roast_has_good_facts_and_no_alert_criterion():
    facts = facts_of()

    assert set(facts) == {CriterionId.DRYING_GAS, CriterionId.RISE_TO_FIRST_CRACK, CriterionId.GAS_AFTER_FIRST_CRACK}
    assert facts[CriterionId.DRYING_GAS]["inBandPercent"] == 100
    assert facts[CriterionId.DRYING_GAS]["outOfBandEpisodes"] == []
    rise = facts[CriterionId.RISE_TO_FIRST_CRACK]
    assert rise["firstCrackMinute"] == 10.1 and rise["minutesOutsideFirstCrackWindow"] == 0
    assert rise["longestStallSeconds"] == 0
    after = facts[CriterionId.GAS_AFTER_FIRST_CRACK]
    assert after["firstDropSecondsAfterCrack"] == 0 and after["fullDropSecondsAfterCrack"] == 0
    assert after["totalDropPoints"] == 5 and after["burnWarningReached"] is False


FAST_DRYING = [(0, 100.0), (120, 180.0), (300, 190.0), (600, 200.0), (756, 205.0)]


def test_measures_a_fast_drying_episode_and_its_correction():
    drying = facts_of(points=FAST_DRYING, gas_changes=[(70, 20), (604, 15)])[CriterionId.DRYING_GAS]

    assert drying["tooFastPercent"] == 100
    [episode] = drying["outOfBandEpisodes"]
    assert episode["kind"] == "FAST" and episode["startSecond"] == 35
    assert episode["correctionSeconds"] == 35
    assert drying["uncorrectedEpisodes"] == 0 and drying["wrongDirectionChanges"] == 0


def test_flags_an_uncorrected_episode_and_a_change_in_the_wrong_direction():
    drying = facts_of(points=FAST_DRYING, gas_changes=[(60, 40)])[CriterionId.DRYING_GAS]

    assert drying["uncorrectedEpisodes"] == 1
    assert drying["slowestCorrectionSeconds"] is None
    assert drying["wrongDirectionChanges"] == 1


def test_a_correction_inside_the_measuring_window_counts_as_immediate():
    drying = facts_of(points=FAST_DRYING, gas_changes=[(10, 20)])[CriterionId.DRYING_GAS]

    assert drying["outOfBandEpisodes"][0]["correctionSeconds"] == 0


def test_splits_episodes_separated_by_an_in_band_stretch():
    points = [(0, 100.0), (60, 130.0), (120, 140.0), (180, 170.0), (600, 200.0), (756, 205.0)]

    drying = facts_of(points=points)[CriterionId.DRYING_GAS]

    assert [episode["kind"] for episode in drying["outOfBandEpisodes"]] == ["FAST", "FAST"]


def test_a_raw_roast_only_keeps_the_drying_criterion():
    points = [(0, 108.59), (152, 138.88), (300, 158.0)]

    facts = facts_of(points=points, duration_seconds=300, gas_changes=[], first_crack_second=None)

    assert set(facts) == {CriterionId.DRYING_GAS}


def test_measures_the_longest_stall_before_the_first_crack():
    points = GOOD_POINTS[:10] + [(380, 170.0), (420, 170.0), (465, 183.81)] + GOOD_POINTS[12:]

    rise = facts_of(points=points)[CriterionId.RISE_TO_FIRST_CRACK]

    assert rise["longestStallSeconds"] == 40


def test_without_first_crack_the_rise_criterion_says_so():
    points = [(0, 108.59), (300, 158.12), (600, 190.0)]

    facts = facts_of(points=points, duration_seconds=600, gas_changes=[(400, 10)], first_crack_second=None)

    rise = facts[CriterionId.RISE_TO_FIRST_CRACK]
    assert rise["firstCrackReached"] is False and rise["gasLoweredInSegment"] is True
    assert CriterionId.GAS_AFTER_FIRST_CRACK not in facts


def test_reports_no_drop_after_the_first_crack_and_the_burn_warning():
    points = GOOD_POINTS[:-1] + [(756, 218.0)]

    after = facts_of(points=points, gas_changes=[(700, 40)])[CriterionId.GAS_AFTER_FIRST_CRACK]

    assert after["firstDropSecondsAfterCrack"] is None
    assert after["raisedAboveStartingGas"] is True
    assert after["burnWarningReached"] is True and after["droppedBeforeBurnWarning"] is False


def test_lowering_just_before_the_first_crack_counts_as_on_time():
    after = facts_of(gas_changes=[(594, 20)])[CriterionId.GAS_AFTER_FIRST_CRACK]

    assert after["gasBeforeFirstCrack"] == 30
    assert after["firstDropSecondsAfterCrack"] == 0 and after["totalDropPoints"] == 10


def test_a_very_short_development_leaves_out_the_gas_after_crack_criterion():
    facts = facts_of(duration_seconds=620, points=GOOD_POINTS[:15] + [(620, 200.6)])

    assert CriterionId.GAS_AFTER_FIRST_CRACK not in facts


def test_checks_each_actionable_alert_against_the_expected_reaction():
    alerts = [("BURN_WARNING", 650), ("DISCHARGE_WINDOW_CLOSING", 700), ("ON_TRACK_DRYING", 100),
              ("DRYING_RISE_SLOW", 900)]

    reaction = facts_of(alerts=alerts, gas_changes=[(604, 25), (660, 15)])[CriterionId.ALERT_REACTION]

    assert reaction["actionableAlerts"] == 2 and reaction["reactedAlerts"] == 1
    assert reaction["reactedPercent"] == 50
    burn, window = reaction["alerts"]
    assert burn["reacted"] is True and burn["reactionSeconds"] == 10 and burn["expected"] == "bajar el gas"
    assert window["reacted"] is False


@pytest.mark.parametrize("discharge, reacted", [(715, True), (730, False)])
def test_discharging_answers_a_discharge_alert_within_the_window(discharge, reacted):
    points = GOOD_POINTS[:-1] + [(discharge, 204.0)]

    reaction = facts_of(points=points, duration_seconds=discharge,
                        alerts=[("DISCHARGE_WINDOW_CLOSING", 700)])[CriterionId.ALERT_REACTION]

    assert reaction["alerts"][0]["reacted"] is reacted


def test_skips_criteria_that_are_not_in_the_rubric():
    rubric = RUBRIC.model_copy(update={"criteria": RUBRIC.criteria[:1]})

    assert set(DecisionFactsExtractor(rubric).extract(trace())) == {CriterionId.DRYING_GAS}
