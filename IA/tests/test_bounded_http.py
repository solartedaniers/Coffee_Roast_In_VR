import json

import httpx
import pytest

from app.net.bounded_http import BoundedHttpClient, HttpTimeout, RemoteServiceError, origin_of

TIMEOUT = HttpTimeout(connect_seconds=1, read_seconds=1)
SECRET = "secret-key-value"


def client_with(handler, max_bytes=1024) -> BoundedHttpClient:
    return BoundedHttpClient(["https://api.example.com/v1", "http://localhost:11434"], max_bytes,
                             transport=httpx.MockTransport(handler))


def test_posts_json_and_reads_json():
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["body"] = json.loads(request.content)
        seen["headers"] = request.headers
        return httpx.Response(200, json={"ok": True})

    result = client_with(handler).post_json("https://api.example.com/v1/chat", {"texto": "café"},
                                            {"Authorization": "Bearer x"}, TIMEOUT)

    assert result == {"ok": True}
    assert seen["body"] == {"texto": "café"}
    assert seen["headers"]["authorization"] == "Bearer x"
    assert seen["headers"]["user-agent"] == "ToastedVR-AI/1.0"


def test_rejects_hosts_that_are_not_configured():
    with pytest.raises(RemoteServiceError, match="not allowed"):
        client_with(lambda request: httpx.Response(200, json={})).post_json(
            "https://evil.example.com/v1", {}, {}, TIMEOUT)


def test_rejects_the_same_host_with_another_scheme():
    with pytest.raises(RemoteServiceError, match="not allowed"):
        client_with(lambda request: httpx.Response(200, json={})).post_json(
            "http://api.example.com/v1/chat", {}, {}, TIMEOUT)


def test_does_not_follow_redirects():
    def handler(request):
        return httpx.Response(302, headers={"Location": "https://evil.example.com"})

    with pytest.raises(RemoteServiceError, match="302"):
        client_with(handler).post_json("https://api.example.com/v1/chat", {}, {}, TIMEOUT)


def test_never_exposes_the_key_or_the_response_body_in_errors():
    def handler(request):
        return httpx.Response(401, text=f"invalid key {SECRET}")

    with pytest.raises(RemoteServiceError) as error:
        client_with(handler).post_json("https://api.example.com/v1/chat", {}, {"Authorization": SECRET}, TIMEOUT)

    assert SECRET not in str(error.value)


def test_limits_the_response_size():
    with pytest.raises(RemoteServiceError, match="too large"):
        client_with(lambda request: httpx.Response(200, content=b"x" * 2048)).post_json(
            "https://api.example.com/v1/chat", {}, {}, TIMEOUT)


def test_reports_invalid_json():
    with pytest.raises(RemoteServiceError, match="invalid JSON"):
        client_with(lambda request: httpx.Response(200, content=b"<html>")).post_json(
            "https://api.example.com/v1/chat", {}, {}, TIMEOUT)


def test_reports_connection_failures():
    def handler(request):
        raise httpx.ConnectError("refused")

    with pytest.raises(RemoteServiceError, match="unreachable"):
        client_with(handler).post_json("http://localhost:11434/api/generate", {}, {}, TIMEOUT)


def test_normalizes_default_ports():
    assert origin_of("https://API.example.com/v1") == "https://api.example.com:443"
    assert origin_of("http://localhost:11434/") == "http://localhost:11434"
