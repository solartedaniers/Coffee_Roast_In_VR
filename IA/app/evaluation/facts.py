"""Hechos medidos en la traza para cada criterio de la rúbrica. Los calcula el código, no el modelo.

Un criterio sin hechos (porque no aplica) queda fuera del resultado y del denominador de la nota.
"""
from dataclasses import dataclass

from app.evaluation.contracts import RoastTrace, TraceEventType
from app.evaluation.rubric import CriterionId, ExpectedReaction, Rubric

Facts = dict[str, object]


def minutes(seconds: float) -> float:
    return round(seconds / 60, 1)


@dataclass(frozen=True)
class GasTimeline:
    """Gas en cada segundo a partir de los cambios del jugador; antes del primero, el de la primera muestra."""

    initial: float
    changes: list[tuple[int, float, float]]  # (segundo, gas anterior, gas nuevo)

    @classmethod
    def of(cls, trace: RoastTrace) -> "GasTimeline":
        current = trace.samples[0].gas
        changes = []
        for change in trace.gas_changes:
            if change.gas != current:
                changes.append((change.t, current, change.gas))
                current = change.gas
        return cls(trace.samples[0].gas, changes)

    def at(self, second: float) -> float:
        gas = self.initial
        for t, _, after in self.changes:
            if t > second:
                break
            gas = after
        return gas

    def between(self, start: float, end: float) -> list[tuple[int, float, float]]:
        return [change for change in self.changes if start <= change[0] <= end]

    def first_lowering(self, start: float, end: float) -> int | None:
        return next((t for t, before, after in self.between(start, end) if after < before), None)

    def first_raising(self, start: float, end: float) -> int | None:
        return next((t for t, before, after in self.between(start, end) if after > before), None)


class DecisionFactsExtractor:
    def __init__(self, rubric: Rubric):
        self.rubric = rubric
        self.parameters = rubric.parameters

    def extract(self, trace: RoastTrace) -> dict[CriterionId, Facts]:
        roast = RoastReading(trace, self.parameters.rise_window_seconds, self.parameters.turning_point_min_rise,
                             self.parameters.drying_end_temperature)
        extractors = {
            CriterionId.DRYING_GAS: self._drying_gas,
            CriterionId.RISE_TO_FIRST_CRACK: self._rise_to_first_crack,
            CriterionId.GAS_AFTER_FIRST_CRACK: self._gas_after_first_crack,
            CriterionId.ALERT_REACTION: self._alert_reaction,
        }
        facts = {}
        for criterion in self.rubric.criteria:
            criterion_facts = extractors[criterion.id](roast)
            if criterion_facts is not None:
                facts[criterion.id] = criterion_facts
        return facts

    def _drying_gas(self, roast: "RoastReading") -> Facts | None:
        p = self.parameters
        if roast.turning_point is None:
            return None
        end = roast.drying_end if roast.drying_end is not None else roast.discharge
        rises = [(t, rise) for t, rise in roast.rises() if roast.turning_point + p.rise_window_seconds <= t <= end]
        if not rises:
            return None
        kinds = [("FAST" if rise > p.drying_rise_max else "SLOW" if rise < p.drying_rise_min else "IN_BAND", t)
                 for t, rise in rises]
        episodes: list[dict] = []
        previous_t = None
        for kind, t in kinds:
            if kind != "IN_BAND":
                last = episodes[-1] if episodes else None
                if last is not None and last["kind"] == kind and last["lastSecond"] == previous_t:
                    last["lastSecond"] = t
                else:
                    episodes.append({"kind": kind, "startSecond": t, "lastSecond": t})
            previous_t = t
        for episode in episodes:
            self._attach_correction(episode, roast.gas, end)
        total = len(kinds)
        return {
            "turningPointSecond": roast.turning_point,
            "dryingEndSecond": roast.drying_end,
            "dryingEndReached": roast.drying_end is not None,
            "inBandPercent": round(100 * sum(kind == "IN_BAND" for kind, _ in kinds) / total),
            "tooFastPercent": round(100 * sum(kind == "FAST" for kind, _ in kinds) / total),
            "tooSlowPercent": round(100 * sum(kind == "SLOW" for kind, _ in kinds) / total),
            "outOfBandEpisodes": episodes,
            "uncorrectedEpisodes": sum(episode["correctionSeconds"] is None for episode in episodes),
            "slowestCorrectionSeconds": max((episode["correctionSeconds"] for episode in episodes
                                             if episode["correctionSeconds"] is not None), default=None),
            "wrongDirectionChanges": sum(episode["wrongDirectionChanges"] for episode in episodes),
        }

    def _attach_correction(self, episode: dict, gas: GasTimeline, drying_end: int) -> None:
        # La subida se mide con una ventana hacia atrás: una corrección hecha dentro de esa ventana
        # antes de que se detecte el episodio también cuenta, con 0 s de demora.
        window_start = episode["startSecond"] - self.parameters.rise_window_seconds
        lowering = episode["kind"] == "FAST"
        corrected = (gas.first_lowering if lowering else gas.first_raising)(window_start, drying_end)
        wrong = [t for t, before, after in gas.between(window_start, episode["lastSecond"])
                 if (after > before) == lowering]
        episode["correctionSeconds"] = None if corrected is None else max(0, corrected - episode["startSecond"])
        episode["wrongDirectionChanges"] = len(wrong)

    def _rise_to_first_crack(self, roast: "RoastReading") -> Facts | None:
        p = self.parameters
        if roast.drying_end is None:
            return None
        first_crack = roast.first_crack
        end = first_crack if first_crack is not None else roast.discharge
        stall = roast.longest_stall(roast.drying_end, end)
        facts: Facts = {
            "dryingEndSecond": roast.drying_end,
            "firstCrackReached": first_crack is not None,
            "firstCrackMinute": None if first_crack is None else minutes(first_crack),
            "longestStallSeconds": stall,
            "gasAtDryingEnd": roast.gas.at(roast.drying_end),
            "gasAtSegmentEnd": roast.gas.at(end),
            "gasLoweredInSegment": roast.gas.first_lowering(roast.drying_end, end - 1) is not None,
        }
        if first_crack is not None:
            low, high = p.first_crack_min_minutes * 60, p.first_crack_max_minutes * 60
            outside = max(0, low - first_crack, first_crack - high)
            facts["minutesOutsideFirstCrackWindow"] = minutes(outside)
        return facts

    def _gas_after_first_crack(self, roast: "RoastReading") -> Facts | None:
        p = self.parameters
        first_crack = roast.first_crack
        if first_crack is None:
            return None
        # Bajar el gas justo antes del primer crack es la misma decisión: se mide desde un poco antes.
        start = max(0, first_crack - p.gas_drop_anticipation_seconds)
        reference = roast.gas.at(start - 1) if start > 0 else roast.gas.initial
        changes = roast.gas.between(start, roast.discharge)
        first_drop = roast.gas.first_lowering(start, roast.discharge)
        full_drop = next((t for t, _, after in changes if reference - after >= p.gas_drop_min_points), None)
        # Con un desarrollo muy corto el criterio solo aplica si el jugador ya alcanzó a bajar el gas.
        if roast.discharge - first_crack < p.min_development_seconds and full_drop is None:
            return None
        lowest = min([reference, *(after for _, _, after in changes)])
        warning = next((sample.t for sample in roast.samples
                        if sample.t >= first_crack and sample.bean >= p.burn_warning_temperature), None)
        after_crack = [sample.bean for sample in roast.samples if sample.t >= first_crack]
        return {
            "firstCrackSecond": first_crack,
            "developmentSeconds": roast.discharge - first_crack,
            "gasBeforeFirstCrack": reference,
            "firstDropSecondsAfterCrack": None if first_drop is None else max(0, first_drop - first_crack),
            "fullDropSecondsAfterCrack": None if full_drop is None else max(0, full_drop - first_crack),
            "totalDropPoints": reference - lowest,
            "raisedAboveStartingGas": any(after > reference for _, _, after in changes),
            "peakAfterFirstCrack": max(after_crack, default=None),
            "burnWarningReached": warning is not None,
            "droppedBeforeBurnWarning": first_drop is not None and (warning is None or first_drop < warning),
        }

    def _alert_reaction(self, roast: "RoastReading") -> Facts | None:
        window = self.parameters.alert_reaction_seconds
        alerts = []
        for alert in roast.trace.alerts:
            expected = self.rubric.alert_reactions.get(alert.id)
            if expected is None or alert.t > roast.discharge:
                continue
            reaction = self._reaction_second(expected, alert.t, alert.t + window, roast)
            alerts.append({
                "id": alert.id, "second": alert.t, "expected": self.rubric.reaction_labels[expected],
                "reacted": reaction is not None,
                "reactionSeconds": None if reaction is None else reaction - alert.t,
            })
        if not alerts:
            return None
        reacted = sum(alert["reacted"] for alert in alerts)
        return {"actionableAlerts": len(alerts), "reactedAlerts": reacted,
                "reactedPercent": round(100 * reacted / len(alerts)), "alerts": alerts}

    @staticmethod
    def _reaction_second(expected: ExpectedReaction, start: int, end: int, roast: "RoastReading") -> int | None:
        discharge = roast.discharge if start <= roast.discharge <= end else None
        lowering = roast.gas.first_lowering(start, end)
        if expected is ExpectedReaction.LOWER_GAS:
            return lowering
        if expected is ExpectedReaction.RAISE_GAS:
            return roast.gas.first_raising(start, end)
        if expected is ExpectedReaction.DISCHARGE:
            return discharge
        return min((second for second in (lowering, discharge) if second is not None), default=None)


class RoastReading:
    """Lectura de la curva: punto de retorno, fin del secado, subida por minuto y estancamientos."""

    def __init__(self, trace: RoastTrace, rise_window_seconds: int, turning_point_min_rise: float,
                 drying_end_temperature: float):
        self.trace = trace
        self.samples = trace.samples
        self.rise_window_seconds = rise_window_seconds
        self.gas = GasTimeline.of(trace)
        discharge = trace.event_second(TraceEventType.DISCHARGE)
        self.discharge = discharge if discharge is not None else self.samples[-1].t
        self.first_crack = trace.event_second(TraceEventType.FIRST_CRACK)
        self.turning_point = self._turning_point(turning_point_min_rise)
        self.drying_end = None if self.turning_point is None else next(
            (sample.t for sample in self.samples
             if sample.t >= self.turning_point and sample.bean >= drying_end_temperature), None)

    def _turning_point(self, min_rise: float) -> int | None:
        lowest = self.samples[0].bean
        for sample in self.samples:
            lowest = min(lowest, sample.bean)
            if sample.bean - lowest >= min_rise:
                return sample.t
        return None

    def rises(self) -> list[tuple[int, float]]:
        """Subida en °C/min de cada muestra contra la que está una ventana atrás (o la más cercana antes)."""
        result = []
        for index, sample in enumerate(self.samples):
            reference = None
            for earlier in reversed(self.samples[:index]):
                if earlier.t <= sample.t - self.rise_window_seconds:
                    reference = earlier
                    break
            if reference is not None:
                result.append((sample.t, (sample.bean - reference.bean) / (sample.t - reference.t) * 60))
        return result

    def longest_stall(self, start: int, end: int) -> int:
        longest = current = 0
        segment = [sample for sample in self.samples if start <= sample.t <= end]
        for before, after in zip(segment, segment[1:]):
            current = current + (after.t - before.t) if after.bean <= before.bean else 0
            longest = max(longest, current)
        return longest
