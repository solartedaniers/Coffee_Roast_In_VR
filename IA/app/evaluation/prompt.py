"""Prompt y esquema JSON estricto con los que el modelo asigna un nivel a cada criterio que aplica."""
import json
from dataclasses import dataclass

from app.evaluation.facts import Facts
from app.evaluation.rubric import CriterionId, Rubric


@dataclass(frozen=True)
class RubricPrompt:
    system: str
    user: str
    schema: dict


class RubricPromptBuilder:
    def __init__(self, rubric: Rubric):
        self.rubric = rubric

    def build(self, facts: dict[CriterionId, Facts]) -> RubricPrompt:
        system = "\n".join(self.rubric.render(line) for line in self.rubric.instructions)
        user = "\n\n".join(self._criterion_block(criterion_id, criterion_facts)
                           for criterion_id, criterion_facts in facts.items())
        return RubricPrompt(system, user, self.schema(list(facts)))

    def _criterion_block(self, criterion_id: CriterionId, facts: Facts) -> str:
        labels = self.rubric.prompt_labels
        criterion = self.rubric.criterion(criterion_id)
        levels = "\n".join(f"{level}: {self.rubric.render(criterion.levels[level])}"
                           for level in sorted(criterion.levels, reverse=True))
        return (f"{labels.criterion} {criterion.id} - {criterion.title}\n"
                f"{labels.description}: {self.rubric.render(criterion.description)}\n"
                f"{labels.levels}:\n{levels}\n"
                f"{labels.facts}: {json.dumps(facts, ensure_ascii=False, sort_keys=True)}")

    def schema(self, criterion_ids: list[CriterionId]) -> dict:
        # La justificación va antes del nivel para que el modelo razone sobre los hechos antes de decidir.
        criterion_schema = {
            "type": "object",
            "properties": {
                "justification": {"type": "string"},
                "level": {"type": "integer", "enum": list(range(self.rubric.max_level + 1))},
            },
            "required": ["justification", "level"],
            "additionalProperties": False,
        }
        ids = [str(criterion_id) for criterion_id in criterion_ids]
        return {
            "type": "object",
            "properties": {
                "criteria": {
                    "type": "object",
                    "properties": {criterion_id: criterion_schema for criterion_id in ids},
                    "required": ids,
                    "additionalProperties": False,
                },
            },
            "required": ["criteria"],
            "additionalProperties": False,
        }
