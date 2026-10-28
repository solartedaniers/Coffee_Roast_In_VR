import pytest

from app.alerts.catalog import RoastPhase, Signal, SignalSettings
from app.alerts.signals import RoastSample, SignalTracker, js_round

SETTINGS = SignalSettings(rise_window_seconds=30, maillard_start_temperature=160, maillard_end_temperature=200,
                          burn_ceiling_temperature=220, turning_point_min_rise=0.5)

READY, DIP, ROASTING = RoastPhase.READY, RoastPhase.CHARGE_DIP, RoastPhase.ROASTING


def feed(temperatures, phase=ROASTING, first_crack=False, start_seconds=1, tracker=None, moisture=None):
    tracker = tracker or SignalTracker(SETTINGS)
    signals = []
    for offset, temperature in enumerate(temperatures):
        sample = RoastSample(phase, temperature, start_seconds + offset, first_crack, moisture)
        signals.append(tracker.update(sample))
    return tracker, signals


@pytest.mark.parametrize("value, expected", [(2.5, 3), (-2.5, -2), (2.49, 2), (-2.51, -3), (0.0, 0)])
def test_rounds_like_javascript(value, expected):
    assert js_round(value) == expected


def test_rise_uses_the_sample_thirty_seconds_back_like_roast_metrics():
    _, signals = feed([100 + 0.5 * second for second in range(40)])
    assert signals[0].rise_per_minute == 0
    assert signals[10].rise_per_minute == 30
    assert signals[39].rise_per_minute == 30


def test_rise_starts_from_the_first_sample_while_the_window_fills():
    _, signals = feed([100, 100, 100, 103])
    assert signals[3].rise_per_minute == 60


def test_rise_starts_again_when_the_beans_are_charged():
    tracker, ready = feed([190, 191, 192], phase=READY, start_seconds=0)
    _, signals = feed([108, 108.5, 109], phase=DIP, tracker=tracker)
    assert ready[2].rise_per_minute == 60
    assert [s.rise_per_minute for s in signals] == [0, 30, 30]


def test_stagnation_compares_with_two_seconds_before_inside_maillard():
    _, signals = feed([170, 171, 171, 171, 171, 172, 173])
    assert [s.stagnation_seconds for s in signals] == [1, 0, 0, 1, 2, 0, 0]


def test_stagnation_keeps_its_value_outside_maillard():
    tracker, _ = feed([170, 170, 170])
    _, signals = feed([150, 150, 205], tracker=tracker, start_seconds=4)
    assert [s.stagnation_seconds for s in signals] == [3, 3, 3]


def test_stagnation_freezes_after_the_first_crack():
    tracker, _ = feed([190, 190, 190])
    tracker.update(RoastSample(ROASTING, 190, 4, True))
    _, signals = feed([190, 190], first_crack=True, tracker=tracker, start_seconds=5)
    assert [s.stagnation_seconds for s in signals] == [4, 4]


def test_stagnation_counts_only_while_roasting():
    _, signals = feed([170, 170, 170], phase=DIP)
    assert [s.stagnation_seconds for s in signals] == [0, 0, 0]


def test_counts_consecutive_seconds_above_the_ceiling_after_charging():
    _, ready = feed([225, 225], phase=READY, start_seconds=0)
    _, signals = feed([219, 220, 221, 222, 219, 221])
    assert [s.seconds_above_ceiling for s in ready] == [0, 0]
    assert [s.seconds_above_ceiling for s in signals] == [0, 0, 1, 2, 0, 1]


def test_peak_starts_when_the_beans_are_charged():
    tracker, ready = feed([190], phase=READY, start_seconds=0)
    _, signals = feed([110, 105, 130], phase=DIP, tracker=tracker)
    assert ready[0].peak_temperature is None
    assert [s.peak_temperature for s in signals] == [110, 110, 130]


def test_turning_point_needs_the_minimum_rise_after_the_lowest_temperature():
    tracker, ready = feed([190, 191], phase=READY, start_seconds=0)
    _, signals = feed([110, 100, 95, 95.3, 95.6, 94, 120], phase=DIP, tracker=tracker)
    assert not any(s.turning_point_reached for s in ready)
    assert [s.turning_point_reached for s in signals] == [False, False, False, False, True, True, True]


def test_development_ratio_uses_the_roast_clock_of_the_first_crack():
    tracker, _ = feed([195], start_seconds=599)
    _, signals = feed([197, 205], first_crack=True, tracker=tracker, start_seconds=600)
    assert signals[0].development_ratio == 0
    assert signals[1].development_ratio == pytest.approx(1 / 601)
    _, before = feed([190])
    assert before[0].development_ratio == 0


def test_counts_seconds_in_each_phase_and_passes_through_the_rest():
    tracker, ready = feed([190, 191], phase=READY, start_seconds=0)
    _, signals = feed([110, 111], phase=DIP, tracker=tracker, moisture=9.5)
    assert [s.seconds_in_phase for s in ready + signals] == [1, 2, 1, 2]
    assert signals[1].value(Signal.MOISTURE_PCT) == 9.5
    assert signals[1].value(Signal.PHASE) is DIP
    assert signals[1].value(Signal.ROAST_SECONDS) == 2


def test_every_signal_has_a_value():
    _, signals = feed([170])
    for signal in Signal:
        signals[0].value(signal)
