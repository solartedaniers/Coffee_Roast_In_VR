from enum import StrEnum


class KnowledgeLevel(StrEnum):
    BEGINNER = "BEGINNER"
    INTERMEDIATE = "INTERMEDIATE"
    ADVANCED = "ADVANCED"


class RoastingResult(StrEnum):
    PERFECT = "PERFECT"
    RAW = "RAW"
    BURNED = "BURNED"
    BAKED = "BAKED"
