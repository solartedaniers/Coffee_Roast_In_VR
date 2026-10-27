from dataclasses import dataclass, field


@dataclass(frozen=True)
class Document:
    """Texto de una página o de un fragmento de PDF, con los metadatos que se guardan en pgvector."""

    text: str
    metadata: dict[str, object] = field(default_factory=dict)
