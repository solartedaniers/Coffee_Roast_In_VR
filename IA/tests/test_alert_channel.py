from app.alerts.catalog import AlertRule, ChannelPolicy
from app.alerts.channel import VoiceChannel

POLICY = ChannelPolicy(queue_max_wait_seconds=5, min_gap_seconds=8, urgent_priorities={"CRITICAL"},
                       unqueued_priorities={"LOW"})


def alert(rule_id: str, priority: str) -> AlertRule:
    return AlertRule.model_validate(dict(
        id=rule_id, priority=priority, levels=["BEGINNER"],
        when=[{"signal": "temperature", "op": "ge", "value": 0}], sustain_seconds=1, rearm_when=None,
        cooldown_seconds=0, max_repeats=1, requires_quiet_seconds=0, source="Propuesto.",
        phrases={"BEGINNER": "Texto."}))


CRITICAL, HIGH, NORMAL, LOW = (alert(name, name) for name in ("CRITICAL", "HIGH", "NORMAL", "LOW"))


def always(_rule):
    return True


def play(channel: VoiceChannel, timeline: dict[int, list[AlertRule]], seconds: int, is_active=always,
         busy_seconds=frozenset()) -> list[tuple[int, str]]:
    delivered = []
    for second in range(seconds):
        rule = channel.offer(timeline.get(second, []), second, is_active, second in busy_seconds)
        if rule is not None:
            delivered.append((second, rule.id))
    return delivered


def test_the_highest_priority_wins_the_same_second():
    channel = VoiceChannel(POLICY)
    assert play(channel, {0: [NORMAL, HIGH]}, 1) == [(0, "HIGH")]


def test_keeps_the_minimum_gap_and_waits_in_the_queue_up_to_its_limit():
    channel = VoiceChannel(POLICY)
    assert play(channel, {0: [HIGH], 4: [NORMAL]}, 20) == [(0, "HIGH"), (8, "NORMAL")]
    channel = VoiceChannel(POLICY)
    assert play(channel, {0: [HIGH], 2: [NORMAL]}, 20) == [(0, "HIGH")]


def test_critical_skips_the_gap_and_the_busy_assistant():
    channel = VoiceChannel(POLICY)
    assert play(channel, {0: [HIGH], 1: [CRITICAL]}, 2, busy_seconds={1}) == [(0, "HIGH"), (1, "CRITICAL")]


def test_two_critical_alerts_play_on_consecutive_seconds():
    channel = VoiceChannel(POLICY)
    second_critical = alert("CRITICAL_2", "CRITICAL")
    assert play(channel, {0: [CRITICAL, second_critical]}, 3) == [(0, "CRITICAL"), (1, "CRITICAL_2")]


def test_low_never_waits_in_the_queue():
    channel = VoiceChannel(POLICY)
    assert play(channel, {0: [HIGH], 4: [LOW]}, 20) == [(0, "HIGH")]
    assert channel.queue == []


def test_drops_a_queued_alert_whose_conditions_stopped():
    channel = VoiceChannel(POLICY)
    assert play(channel, {0: [HIGH], 4: [NORMAL]}, 20, is_active=lambda rule: rule is not NORMAL) == [(0, "HIGH")]


def test_a_busy_assistant_holds_the_other_alerts():
    channel = VoiceChannel(POLICY)
    assert play(channel, {0: [NORMAL]}, 10, busy_seconds={0, 1, 2}) == [(3, "NORMAL")]


def test_counts_the_seconds_since_the_last_delivery():
    channel = VoiceChannel(POLICY)
    assert channel.seconds_since_last_delivery(5) == float("inf")
    channel.offer([NORMAL], 5, always)
    assert channel.seconds_since_last_delivery(9) == 4
