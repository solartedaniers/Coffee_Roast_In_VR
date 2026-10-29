import pytest
from pydantic import ValidationError

from app.alerts.catalog import RoastPhase
from app.alerts.synthetic_curve import CurveSet, SyntheticCurve, interpolate
from app.alerts.conformance import DEFAULT_CURVES

CURVE = SyntheticCurve(id="test", description="Prueba.", samples=8, ready_samples=2, charge_dip_samples=3,
                       first_crack_sample=6, points=[(0, 190.0), (1, 192.0), (2, 110.0), (7, 120.0)],
                       moisture_points=[(2, 10.0), (7, 9.0)], recording=[(3, 4)], assistant_busy=[(5, 5)])


def test_interpolates_in_a_straight_line_and_holds_the_ends():
    points = [(2, 10.0), (6, 30.0)]
    assert interpolate(points, 0) == 10.0
    assert interpolate(points, 3) == 15.0
    assert interpolate(points, 9) == 30.0


def test_builds_one_sample_per_simulated_second():
    samples = CURVE.roast_samples()
    assert [s.phase for s in samples] == [RoastPhase.READY] * 2 + [RoastPhase.CHARGE_DIP] * 3 + [RoastPhase.ROASTING] * 3
    assert [s.roast_seconds for s in samples] == [0, 0, 1, 2, 3, 4, 5, 6]
    assert [s.temperature for s in samples] == [190.0, 192.0, 110.0, 112.0, 114.0, 116.0, 118.0, 120.0]
    assert [s.first_crack_reached for s in samples] == [False] * 6 + [True] * 2
    assert samples[0].moisture_pct == 10.0 and samples[7].moisture_pct == 9.0


def test_marks_the_recording_and_busy_seconds():
    assert [CURVE.is_recording(i) for i in range(2, 6)] == [False, True, True, False]
    assert [CURVE.is_assistant_busy(i) for i in range(4, 7)] == [False, True, False]


def test_moisture_is_optional():
    curve = CURVE.model_copy(update={"moisture_points": None})
    assert all(s.moisture_pct is None for s in curve.roast_samples())


@pytest.mark.parametrize("points", [[(1, 100.0), (7, 120.0)], [(0, 100.0), (6, 120.0)],
                                    [(0, 100.0), (4, 110.0), (4, 111.0), (7, 120.0)]])
def test_points_must_cover_the_whole_curve_in_order(points):
    with pytest.raises(ValidationError):
        SyntheticCurve(id="bad", description="", samples=8, ready_samples=0, charge_dip_samples=0,
                       first_crack_sample=None, points=points)


def test_loads_the_seven_curves_of_the_repository():
    curves = CurveSet.from_file(DEFAULT_CURVES).curves
    assert [curve.id for curve in curves] == ["good", "burned", "baked", "raw", "drop", "low_charge",
                                              "overdeveloped"]
