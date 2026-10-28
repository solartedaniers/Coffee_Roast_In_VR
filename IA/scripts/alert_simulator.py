"""Simula las alertas proactivas sobre curvas de tueste: qué suena, en qué segundo y cuántas por nivel.

Curvas de prueba del repositorio (IA/eval/alerts/curves.json):
    .venv/Scripts/python scripts/alert_simulator.py
Curvas exportadas a CSV, una fila por segundo simulado desde READY (columnas phase, temperature,
roastSeconds, firstCrackReached y, si las hay, moisturePct, recording, assistantBusy):
    .venv/Scripts/python scripts/alert_simulator.py --csv curva1.csv curva2.csv
Para probar umbrales sin tocar el catálogo: --catalog copia.json. Para simular a Unity sin humedad:
--no-moisture. Con --phrases muestra la frase de cada alerta y con --summary solo el conteo.
"""
import argparse
import csv
import sys
from dataclasses import dataclass, replace
from pathlib import Path

from app.alerts.catalog import AlertCatalog, RoastPhase
from app.alerts.conformance import DEFAULT_CURVES
from app.alerts.evaluator import AlertDelivery, AlertEvaluator, DeliveryMode
from app.alerts.signals import RoastSample
from app.alerts.synthetic_curve import CurveSet, SyntheticCurve
from app.config import get_settings
from app.domain import KnowledgeLevel

TRUE_TEXT = "true"
MODE_MARKS = {DeliveryMode.VOICE: "voz", DeliveryMode.SUBTITLE: "texto"}
NOT_HEARD = "-"


@dataclass(frozen=True)
class CurveInput:
    name: str
    samples: list[RoastSample]
    recording: list[bool]
    assistant_busy: list[bool]


def from_synthetic(curve: SyntheticCurve) -> CurveInput:
    indexes = range(curve.samples)
    return CurveInput(curve.id, curve.roast_samples(), [curve.is_recording(i) for i in indexes],
                      [curve.is_assistant_busy(i) for i in indexes])


def read_csv(path: Path) -> CurveInput:
    with path.open(encoding="utf-8", newline="") as file:
        rows = list(csv.DictReader(file))
    samples = [RoastSample(phase=RoastPhase(row["phase"]), temperature=float(row["temperature"]),
                           roast_seconds=int(row["roastSeconds"]),
                           first_crack_reached=row["firstCrackReached"].lower() == TRUE_TEXT,
                           moisture_pct=float(row["moisturePct"]) if row.get("moisturePct") else None)
               for row in rows]
    flags = [[row.get(column, "").lower() == TRUE_TEXT for row in rows] for column in ("recording", "assistantBusy")]
    return CurveInput(path.stem, samples, *flags)


def without_moisture(curve: CurveInput) -> CurveInput:
    return replace(curve, samples=[replace(sample, moisture_pct=None) for sample in curve.samples])


def simulate(catalog: AlertCatalog, curve: CurveInput, level: KnowledgeLevel) -> list[AlertDelivery]:
    evaluator = AlertEvaluator(catalog, level)
    deliveries = (evaluator.step(sample, curve.recording[i], curve.assistant_busy[i])
                  for i, sample in enumerate(curve.samples))
    return [delivery for delivery in deliveries if delivery is not None]


def clock(seconds: int) -> str:
    return f"{seconds // 60:02d}:{seconds % 60:02d}"


def table(header: list[str], rows: list[list[str]]) -> str:
    widths = [max(len(str(cell)) for cell in column) for column in zip(header, *rows)]
    lines = [header, ["-" * width for width in widths], *rows]
    return "\n".join("  ".join(str(cell).ljust(width) for cell, width in zip(line, widths)).rstrip()
                     for line in lines)


def timeline(curve: CurveInput, results: dict[KnowledgeLevel, list[AlertDelivery]], phrases: bool) -> str:
    """Una fila por alerta y segundo, con lo que escucha cada nivel."""
    heard = {(d.second, d.alert_id): d for deliveries in results.values() for d in deliveries}
    header = ["muestra", "tueste", "fase", "°C", "alerta", "prioridad", *[level.value[:3] for level in results]]
    if phrases:
        header.append("frase")
    rows = []
    for second, alert_id in sorted(heard):
        delivery, sample = heard[(second, alert_id)], curve.samples[second]
        marks = [next((MODE_MARKS[d.mode] for d in results[level] if (d.second, d.alert_id) == (second, alert_id)),
                      NOT_HEARD) for level in results]
        row = [str(second), clock(sample.roast_seconds), sample.phase.value, f"{sample.temperature:.1f}", alert_id,
               delivery.priority.value, *marks]
        if phrases:
            row.append(delivery.text)
        rows.append(row)
    return table(header, rows)


def summary(counts: dict[str, dict[KnowledgeLevel, int]], levels: list[KnowledgeLevel]) -> str:
    rows = [[name, *[str(per_level[level]) for level in levels]] for name, per_level in counts.items()]
    return table(["curva", *[level.value for level in levels]], rows)


def parse_args(argv: list[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--csv", type=Path, nargs="+", help="Curvas en CSV en vez de las curvas de prueba")
    parser.add_argument("--curves", type=Path, default=DEFAULT_CURVES, help="Archivo de curvas de prueba")
    parser.add_argument("--catalog", type=Path, help="Catálogo de alertas; por defecto el de la configuración")
    parser.add_argument("--level", choices=[level.value for level in KnowledgeLevel], help="Solo este nivel")
    parser.add_argument("--no-moisture", action="store_true", help="Simula que Unity no envía la humedad")
    parser.add_argument("--phrases", action="store_true", help="Muestra la frase de cada alerta")
    parser.add_argument("--summary", action="store_true", help="Solo el conteo de alertas por curva y nivel")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> None:
    args = parse_args(argv)
    catalog = AlertCatalog.from_file(args.catalog or get_settings().alerts_catalog_file)
    levels = [KnowledgeLevel(args.level)] if args.level else list(KnowledgeLevel)
    if args.csv:
        curves = [read_csv(path) for path in args.csv]
    else:
        curves = [from_synthetic(curve) for curve in CurveSet.from_file(args.curves).curves]
    if args.no_moisture:
        curves = [without_moisture(curve) for curve in curves]

    counts = {}
    for curve in curves:
        results = {level: simulate(catalog, curve, level) for level in levels}
        counts[curve.name] = {level: len(deliveries) for level, deliveries in results.items()}
        if not args.summary:
            print(f"\n== {curve.name}\n{timeline(curve, results, args.phrases)}")
    print(f"\n== Alertas por tueste (catálogo revisión {catalog.catalog_revision})\n{summary(counts, levels)}")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
