import importlib.util
import json

from app.alerts.catalog import AlertCatalog, RoastPhase
from app.alerts.conformance import DEFAULT_CURVES, DEFAULT_OUTPUT
from app.alerts.synthetic_curve import CurveSet
from app.config import SERVICE_ROOT, Settings
from app.domain import KnowledgeLevel

spec = importlib.util.spec_from_file_location("alert_simulator", SERVICE_ROOT / "scripts" / "alert_simulator.py")
alert_simulator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(alert_simulator)

CSV_TEXT = (
    "phase,temperature,roastSeconds,firstCrackReached,moisturePct,recording\n"
    "READY,170.0,0,false,8,false\n"
    "READY,170.0,0,false,8,false\n"
    "READY,170.0,0,false,8,true\n"
    "CHARGE_DIP,110.0,1,false,8,false\n"
)


def catalog() -> AlertCatalog:
    return AlertCatalog.from_file(Settings(_env_file=None).alerts_catalog_file)


def test_reads_a_curve_from_csv_with_optional_columns(tmp_path):
    path = tmp_path / "real.csv"
    path.write_text(CSV_TEXT, encoding="utf-8")
    curve = alert_simulator.read_csv(path)
    assert curve.name == "real"
    assert [s.phase for s in curve.samples] == [RoastPhase.READY] * 3 + [RoastPhase.CHARGE_DIP]
    assert curve.recording == [False, False, True, False]
    assert curve.assistant_busy == [False] * 4
    assert alert_simulator.without_moisture(curve).samples[0].moisture_pct is None


def test_simulates_the_same_alerts_as_the_conformance_vectors():
    vectors = json.loads(DEFAULT_OUTPUT.read_text(encoding="utf-8"))["vectors"]
    for synthetic, vector in zip(CurveSet.from_file(DEFAULT_CURVES).curves, vectors):
        curve = alert_simulator.from_synthetic(synthetic)
        for level in KnowledgeLevel:
            heard = [d.alert_id for d in alert_simulator.simulate(catalog(), curve, level)]
            assert heard == [d["alertId"] for d in vector["expected"][level.value]]


def test_formats_the_roast_clock():
    assert alert_simulator.clock(0) == "00:00"
    assert alert_simulator.clock(605) == "10:05"


def test_prints_the_timeline_and_the_count_per_level(tmp_path, capsys):
    path = tmp_path / "low.csv"
    path.write_text(CSV_TEXT, encoding="utf-8")
    alert_simulator.main(["--csv", str(path), "--phrases"])
    output = capsys.readouterr().out
    assert "CHARGE_TEMP_LOW" in output and "texto" in output
    assert "Carga por debajo de 180 grados." in output
    assert output.rstrip().splitlines()[-1].split() == ["low", "1", "1", "1"]


def test_summary_only_counts_the_chosen_level(capsys):
    alert_simulator.main(["--summary", "--level", "ADVANCED"])
    lines = capsys.readouterr().out.strip().splitlines()
    assert lines[1].split() == ["curva", "ADVANCED"]
    assert len(lines) == 3 + len(CurveSet.from_file(DEFAULT_CURVES).curves)
