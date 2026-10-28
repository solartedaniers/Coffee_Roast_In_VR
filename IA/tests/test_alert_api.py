import json

import pytest
from fastapi.testclient import TestClient

from app.alerts.catalog import AlertCatalog
from app.alerts.package import AlertPackageBuilder, etag_matches
from app.api import create_app
from app.config import Settings
from app.container import Services
from app.feedback.cache import FeedbackCache
from tests.test_alert_package import FakePhrases

PATH = "/api/v1/alerts/package"


@pytest.fixture
def client() -> TestClient:
    catalog = AlertCatalog.from_file(Settings(_env_file=None).alerts_catalog_file)
    services = Services(None, None, FeedbackCache(60, 10), None, alerts=AlertPackageBuilder(catalog, FakePhrases()),
                        alerts_package_timeout_seconds=5)
    return TestClient(create_app(services, ensure_schema=False))


def test_returns_the_package_of_the_level_with_its_etag(client):
    response = client.get(PATH, params={"level": "BEGINNER"})
    assert response.status_code == 200
    assert response.headers["content-type"] == "application/json"
    assert json.loads(response.content)["level"] == "BEGINNER"
    assert response.headers["etag"].startswith('"')


def test_answers_304_when_unity_already_has_that_package(client):
    etag = client.get(PATH, params={"level": "ADVANCED"}).headers["etag"]
    response = client.get(PATH, params={"level": "ADVANCED"}, headers={"If-None-Match": etag})
    assert response.status_code == 304
    assert response.content == b"" and response.headers["etag"] == etag
    other = client.get(PATH, params={"level": "BEGINNER"}, headers={"If-None-Match": etag})
    assert other.status_code == 200


def test_uses_the_default_level_without_one(client):
    assert json.loads(client.get(PATH).content)["level"] == "INTERMEDIATE"


def test_rejects_an_unknown_level(client):
    assert client.get(PATH, params={"level": "EXPERT"}).status_code == 400


def test_is_not_exposed_when_alerts_are_not_configured():
    client = TestClient(create_app(Services(None, None, FeedbackCache(60, 10), None), ensure_schema=False))
    assert client.get(PATH).status_code == 404


@pytest.mark.parametrize("header, matches", [
    (None, False), ("", False), ('"abc"', True), ('W/"abc"', True), ('"x", "abc"', True), ("*", True),
    ('"abcd"', False),
])
def test_compares_if_none_match_like_http(header, matches):
    assert etag_matches(header, '"abc"') is matches
