import pytest

from app.contracts import RoastFeedbackRequest
from app.domain import RoastingResult
from app.rag.query_builder import RagQueryBuilder
from app.roast_reference import DevelopmentTimeRange
from tests.support.recorded_sessions import ALL


@pytest.fixture
def builder(settings) -> RagQueryBuilder:
    return RagQueryBuilder(DevelopmentTimeRange(settings.rag_dtr_optimal_min, settings.rag_dtr_optimal_max))


@pytest.mark.parametrize("recorded", ALL, ids=lambda recorded: recorded.name)
def test_searches_with_the_same_query_as_java(builder, settings, recorded):
    query = builder.build(recorded.request, recorded.level)

    assert f"{query}\ntopK={settings.rag_top_k}" == recorded.expected_query()


def test_ignores_development_time_without_total_duration(builder):
    session = RoastFeedbackRequest(result=RoastingResult.PERFECT, quality_score=90, target_temperature=210.0,
                                   final_temperature=210.0, development_time_seconds=100, total_duration_seconds=0)

    assert "desarrollo" not in builder.build(session, None)
