"""Paquete de alertas de un nivel: reglas, frase y audio ya sintetizado, listo para que Unity lo evalúe."""
import base64
import hashlib
import json
import threading
from dataclasses import dataclass

from app.alerts.catalog import AlertCatalog, AlertRule, Priority
from app.domain import KnowledgeLevel
from app.voice.deadline import Deadline
from app.voice.speech.phrase_cache import PhraseAudioCache

AUDIO_FORMAT = "wav"
RULE_FIELDS = {"id", "priority", "when", "sustain_seconds", "rearm_when", "cooldown_seconds", "max_repeats",
               "requires_quiet_seconds"}


ANY_ETAG = "*"
WEAK_PREFIX = "W/"


def etag_matches(if_none_match: str | None, etag: str) -> bool:
    """If-None-Match puede traer varias etiquetas separadas por comas, débiles (W/) o un asterisco."""
    if not if_none_match:
        return False
    candidates = {tag.strip().removeprefix(WEAK_PREFIX) for tag in if_none_match.split(",")}
    return ANY_ETAG in candidates or etag in candidates


@dataclass(frozen=True)
class AlertPackage:
    body: bytes
    etag: str
    complete: bool


class AlertPackageBuilder:
    """Arma el paquete de cada nivel. Solo guarda en memoria los que quedaron con todos sus audios: si
    una frase no se pudo sintetizar va con audio null, y el siguiente pedido lo vuelve a intentar."""

    def __init__(self, catalog: AlertCatalog, phrases: PhraseAudioCache):
        self.catalog = catalog
        self.phrases = phrases
        self.complete: dict[KnowledgeLevel, AlertPackage] = {}
        self.lock = threading.Lock()

    def level_or_default(self, level: KnowledgeLevel | None) -> KnowledgeLevel:
        return level or self.catalog.default_level

    def build(self, level: KnowledgeLevel | None, deadline: Deadline) -> AlertPackage:
        level = self.level_or_default(level)
        with self.lock:
            cached = self.complete.get(level)
        if cached is not None:
            return cached
        rules = [self._rule(rule, level, deadline) for rule in self.catalog.rules_for(level)]
        payload = {
            "schemaVersion": self.catalog.schema_version,
            "catalogRevision": self.catalog.catalog_revision,
            "level": level.value,
            "signals": self.catalog.signals.model_dump(mode="json", by_alias=True),
            "channel": self._channel(),
            "rules": rules,
        }
        body = json.dumps(payload, ensure_ascii=False, separators=(",", ":"), sort_keys=True).encode("utf-8")
        package = AlertPackage(body, f'"{hashlib.sha256(body).hexdigest()}"',
                               all(rule["audio"] is not None for rule in rules))
        if package.complete:
            with self.lock:
                self.complete[level] = package
        return package

    def warm_up(self, seconds_per_phrase: float) -> None:
        """Sintetiza de una vez las frases de todos los niveles: Azure se gasta una sola vez por texto."""
        self.phrases.warm(self.phrase_texts(), seconds_per_phrase)

    def phrase_texts(self) -> list[str]:
        """Todas las frases de todos los niveles, sin repetir, para precalentar su audio."""
        texts = (rule.phrases[level] for rule in self.catalog.rules for level in rule.levels)
        return list(dict.fromkeys(texts))

    def _rule(self, rule: AlertRule, level: KnowledgeLevel, deadline: Deadline) -> dict:
        data = rule.model_dump(mode="json", by_alias=True, include=RULE_FIELDS)
        text = rule.phrases[level]
        audio = None if deadline.expired else self.phrases.get(text, deadline)
        data["text"] = text
        data["audio"] = None if audio is None else {
            "format": AUDIO_FORMAT, "sampleRateHz": audio.sample_rate_hz, "provider": audio.provider,
            "base64": base64.b64encode(audio.wav).decode("ascii")}
        return data

    def _channel(self) -> dict:
        channel = self.catalog.channel.model_dump(mode="json", by_alias=True)
        # Son conjuntos: se ordenan para que el JSON, y con él el ETag, no cambie entre arranques.
        for key in ("urgentPriorities", "unqueuedPriorities"):
            channel[key] = sorted(channel[key], key=lambda name: Priority(name).rank)
        return channel
