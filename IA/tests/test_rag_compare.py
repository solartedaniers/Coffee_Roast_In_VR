import json

from app.rag.evaluation.compare import group_table, load_reports, markdown_table


def report(label, mrr, negatives_with_context):
    return {
        "label": label,
        "summary": {
            "player": {"hit_at_1": 0.2, "hit_at_3": 0.4, "hit_at_4": 0.5, "hit_at_5": 0.6, "mrr_at_10": mrr},
            "negative": {"cases": 5, "cases_with_context": negatives_with_context},
            "repeated_texts_in_top_k": 1,
            "latency_ms": {"p50": 812.4},
        },
    }


def test_compares_every_measurement_against_the_first_one():
    table = markdown_table([report("base", 0.4, 5), report("hibrida", 0.5, 2)]).splitlines()

    assert table[0].startswith("| Variante | hit@1 |")
    assert table[2] == "| base | 0.20 | 0.40 | 0.50 | 0.60 | 0.400 | +0.000 | 5/5 | 1 | 812 |"
    assert table[3] == "| hibrida | 0.20 | 0.40 | 0.50 | 0.60 | 0.500 | +0.100 | 2/5 | 1 | 812 |"


def test_an_empty_comparison_is_empty():
    assert markdown_table([]) == ""


def test_separates_validated_and_draft_groups_only_for_measurements_that_have_them():
    ranking = {"cases": 3, "hit_at_1": 0.2, "hit_at_3": 0.4, "hit_at_4": 0.5, "hit_at_5": 0.6, "mrr_at_10": 0.45}
    grouped = report("frases", 0.4, 5)
    grouped["summary"]["player"]["cases"] = 25
    grouped["summary"]["groups"] = {"confirmed_with_draft_pages": ranking, "draft_questions": ranking, "total": ranking}

    table = group_table([report("vieja", 0.4, 5), grouped]).splitlines()

    assert table[0].startswith("| Variante | Grupo | preguntas |")
    assert [line.split(" | ")[1] for line in table[2:]] == [
        "validadas", "validadas + páginas en borrador", "preguntas en borrador", "total"]
    assert table[2] == "| frases | validadas | 25 | 0.20 | 0.40 | 0.50 | 0.60 | 0.400 |"
    assert group_table([report("vieja", 0.4, 5)]) == ""


def test_skips_saved_files_that_are_not_rag_measurements(tmp_path):
    measurement, rubric = tmp_path / "a.json", tmp_path / "evaluation.json"
    measurement.write_text(json.dumps(report("base", 0.4, 5)), encoding="utf-8")
    rubric.write_text(json.dumps({"summary": {"exact_agreement": 0.99}}), encoding="utf-8")

    assert [r["label"] for r in load_reports([measurement, rubric])] == ["base"]
