import pytest
from pydantic import ValidationError

from app.evaluation.contracts import EvaluationRequest, OperationMode, TraceEventType

TRACE = {
    "version": 1, "operationMode": "MANUAL", "sampleIntervalSeconds": 5,
    "samples": [{"t": 5, "bean": 90.0, "gas": 30}, {"t": 10, "bean": 88.5, "gas": 30}],
    "gasChanges": [{"t": 7, "gas": 40}],
    "events": [{"type": "CHARGE", "t": 0}, {"type": "DISCHARGE", "t": 10}],
    "alerts": [{"id": "DRYING_RISE_FAST", "t": 8}],
}


def test_reads_the_camel_case_trace():
    trace = EvaluationRequest.model_validate({"trace": TRACE}).trace

    assert trace.operation_mode is OperationMode.MANUAL
    assert trace.gas_changes[0].gas == 40
    assert trace.event_second(TraceEventType.DISCHARGE) == 10
    assert trace.event_second(TraceEventType.FIRST_CRACK) is None


def test_lists_are_optional():
    trace = {key: value for key, value in TRACE.items() if key not in {"gasChanges", "events", "alerts"}}

    assert EvaluationRequest.model_validate({"trace": trace}).trace.alerts == []


@pytest.mark.parametrize("change", [
    {"version": 2},
    {"samples": [{"t": 10, "bean": 90, "gas": 30}, {"t": 5, "bean": 91, "gas": 30}]},
    {"samples": [{"t": 5, "bean": 90, "gas": 30}, {"t": 5, "bean": 91, "gas": 30}]},
    {"samples": [{"t": 5, "bean": 90, "gas": 30}]},
    {"gasChanges": [{"t": 9, "gas": 40}, {"t": 7, "gas": 20}]},
    {"samples": [{"t": 5, "bean": 90, "gas": 130}, {"t": 10, "bean": 91, "gas": 30}]},
    {"events": [{"type": "EXPLOSION", "t": 1}]},
    {"operationMode": "REMOTE"},
], ids=["version", "unordered", "repeated", "one-sample", "gas-unordered", "gas-range", "event", "mode"])
def test_rejects_an_invalid_trace(change):
    with pytest.raises(ValidationError):
        EvaluationRequest.model_validate({"trace": TRACE | change})
