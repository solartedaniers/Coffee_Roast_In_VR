from dataclasses import replace

from app.alerts.catalog import AlertRule, Condition, RoastPhase
from app.alerts.rules import RuleState, holds
from app.alerts.signals import RoastSignals

BASE = RoastSignals(temperature=150.0, rise_per_minute=12, stagnation_seconds=0, seconds_above_ceiling=0,
                    development_ratio=0.0, roast_seconds=300, moisture_pct=None, phase=RoastPhase.ROASTING,
                    first_crack_reached=False, seconds_in_phase=250, peak_temperature=150.0,
                    turning_point_reached=True)


def condition(signal, op, value) -> Condition:
    return Condition(signal=signal, op=op, value=value)


def rule(**changes) -> AlertRule:
    fields = dict(id="TEST", priority="HIGH", levels=["BEGINNER"],
                  when=[condition("temperature", "ge", 215)], sustain_seconds=1, rearm_when=None,
                  cooldown_seconds=0, max_repeats=5, requires_quiet_seconds=0, source="Propuesto.",
                  phrases={"BEGINNER": "Texto."})
    return AlertRule.model_validate(fields | changes)


def run(state: RuleState, temperatures, quiet=float("inf")) -> list[int]:
    return [second for second, temperature in enumerate(temperatures)
            if state.update(replace(BASE, temperature=temperature), second, quiet)]


def test_all_conditions_must_hold_and_missing_signals_never_do():
    rising = [condition("phase", "eq", "ROASTING"), condition("risePerMinute", "gt", 10)]
    assert holds(rising, BASE)
    assert not holds(rising + [condition("firstCrackReached", "eq", True)], BASE)
    assert not holds([condition("moisturePct", "le", 100)], BASE)
    assert holds([condition("moisturePct", "le", 3)], replace(BASE, moisture_pct=2.5))
    assert holds([], BASE)


def test_fires_once_the_conditions_held_for_the_sustain_seconds():
    state = RuleState(rule(sustain_seconds=3))
    assert run(state, [216, 216, 210, 216, 216, 216, 216]) == [5]


def test_without_rearm_conditions_it_rearms_when_the_conditions_stop():
    state = RuleState(rule())
    assert run(state, [216, 216, 210, 216]) == [0, 3]


def test_rearm_conditions_add_hysteresis():
    state = RuleState(rule(rearm_when=[condition("temperature", "lt", 213)]))
    assert run(state, [216, 214, 216, 212, 216]) == [0, 4]


def test_an_empty_rearm_repeats_while_active_spaced_by_the_cooldown():
    state = RuleState(rule(rearm_when=[], cooldown_seconds=3, max_repeats=3))
    assert run(state, [216] * 12) == [0, 3, 6]


def test_cooldown_applies_even_after_rearming():
    state = RuleState(rule(cooldown_seconds=4))
    assert run(state, [216, 210, 216, 216, 216, 216]) == [0, 4]


def test_requires_a_quiet_channel_when_asked():
    state = RuleState(rule(requires_quiet_seconds=60))
    assert run(state, [216, 216], quiet=59) == []
    assert run(state, [216], quiet=60) == [0]


def test_reports_whether_the_conditions_hold_now():
    state = RuleState(rule())
    assert state.is_active(replace(BASE, temperature=220))
    assert not state.is_active(BASE)
