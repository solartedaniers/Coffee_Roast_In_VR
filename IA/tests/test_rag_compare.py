from app.rag.evaluation.compare import markdown_table


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
