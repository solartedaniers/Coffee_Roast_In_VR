import base64
import json

import pytest

from app.alerts.catalog import AlertCatalog
from app.alerts.package import AlertPackageBuilder
from app.config import Settings
from app.domain import KnowledgeLevel
from app.voice.deadline import Deadline
from app.voice.speech.base import SynthesizedAudio
from tests.voice.wav_factory import tone_wav

WAV = tone_wav(0.2, rate=22_050)


class FakePhrases:
    """Doble de PhraseAudioCache: nunca llama a Azure ni a Piper."""

    def __init__(self, missing=()):
        self.missing = set(missing)
        self.texts = []

    def get(self, text, deadline):
        self.texts.append(text)
        return None if text in self.missing else SynthesizedAudio(WAV, 22_050, "azure")


@pytest.fixture(scope="module")
def catalog() -> AlertCatalog:
    return AlertCatalog.from_file(Settings(_env_file=None).alerts_catalog_file)


def body(package) -> dict:
    return json.loads(package.body)


def test_carries_only_the_rules_and_phrases_of_the_level(catalog):
    package = body(AlertPackageBuilder(catalog, FakePhrases()).build(KnowledgeLevel.ADVANCED, Deadline(5)))
    assert package["level"] == "ADVANCED"
    assert (package["schemaVersion"], package["catalogRevision"]) == (1, catalog.catalog_revision)
    assert [r["id"] for r in package["rules"]] == [r.id for r in catalog.rules_for(KnowledgeLevel.ADVANCED)]
    first_crack = next(r for r in package["rules"] if r["id"] == "FIRST_CRACK")
    assert first_crack["text"] == "Primer crack."
    assert first_crack["when"] == [{"signal": "firstCrackReached", "op": "eq", "value": True}]
    assert set(first_crack) == {"id", "priority", "when", "sustainSeconds", "rearmWhen", "cooldownSeconds",
                                "maxRepeats", "requiresQuietSeconds", "text", "audio"}


def test_includes_the_signal_settings_and_the_channel_policy(catalog):
    package = body(AlertPackageBuilder(catalog, FakePhrases()).build(KnowledgeLevel.BEGINNER, Deadline(5)))
    assert package["signals"]["burnCeilingTemperature"] == 220
    assert package["channel"] == {"queueMaxWaitSeconds": 5, "minGapSeconds": 8,
                                  "urgentPriorities": ["CRITICAL"], "unqueuedPriorities": ["LOW"]}


def test_embeds_the_synthesized_audio(catalog):
    package = body(AlertPackageBuilder(catalog, FakePhrases()).build(KnowledgeLevel.BEGINNER, Deadline(5)))
    audio = package["rules"][0]["audio"]
    assert audio["format"] == "wav" and audio["sampleRateHz"] == 22_050 and audio["provider"] == "azure"
    assert base64.b64decode(audio["base64"]) == WAV


def test_the_etag_changes_with_the_level_and_not_between_builds(catalog):
    first, second = AlertPackageBuilder(catalog, FakePhrases()), AlertPackageBuilder(catalog, FakePhrases())
    beginner = first.build(KnowledgeLevel.BEGINNER, Deadline(5))
    assert beginner.etag == second.build(KnowledgeLevel.BEGINNER, Deadline(5)).etag
    assert beginner.etag != first.build(KnowledgeLevel.INTERMEDIATE, Deadline(5)).etag
    assert beginner.etag.startswith('"') and beginner.etag.endswith('"')


def test_without_a_level_it_uses_the_default_of_the_catalog(catalog):
    package = AlertPackageBuilder(catalog, FakePhrases()).build(None, Deadline(5))
    assert body(package)["level"] == catalog.default_level.value == "INTERMEDIATE"


def test_keeps_a_complete_package_and_retries_an_incomplete_one(catalog):
    first_crack = next(r for r in catalog.rules if r.id == "FIRST_CRACK").phrases[KnowledgeLevel.ADVANCED]
    phrases = FakePhrases(missing={first_crack})
    builder = AlertPackageBuilder(catalog, phrases)
    incomplete = builder.build(KnowledgeLevel.ADVANCED, Deadline(5))
    assert not incomplete.complete
    assert next(r for r in body(incomplete)["rules"] if r["id"] == "FIRST_CRACK")["audio"] is None
    phrases.missing.clear()
    complete = builder.build(KnowledgeLevel.ADVANCED, Deadline(5))
    asked = len(phrases.texts)
    assert complete.complete and complete.etag != incomplete.etag
    assert builder.build(KnowledgeLevel.ADVANCED, Deadline(5)) is complete
    assert len(phrases.texts) == asked


def test_skips_the_audio_when_the_time_is_up(catalog):
    phrases = FakePhrases()
    package = AlertPackageBuilder(catalog, phrases).build(KnowledgeLevel.ADVANCED, Deadline(0))
    assert not package.complete and phrases.texts == []


def test_lists_every_phrase_once_for_the_warm_up(catalog):
    texts = AlertPackageBuilder(catalog, FakePhrases()).phrase_texts()
    expected = {rule.phrases[level] for rule in catalog.rules for level in rule.levels}
    assert len(texts) == len(set(texts)) and set(texts) == expected


def test_warms_every_phrase_of_every_level(catalog):
    phrases = FakePhrases()
    phrases.warm = lambda texts, seconds: phrases.texts.extend(texts)
    builder = AlertPackageBuilder(catalog, phrases)
    builder.warm_up(5)
    assert phrases.texts == builder.phrase_texts()
