package com.toastedvr.toastedvr.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toastedvr.toastedvr.backend.config.RoastEvaluationProperties;
import com.toastedvr.toastedvr.backend.domain.RoastGrade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RoastGradeResolverTests {

    private final RoastGradeResolver resolver = new RoastGradeResolver(new RoastEvaluationProperties());

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource({"100, EXCELLENT", "85, EXCELLENT", "84, GOOD", "70, GOOD", "69, FAIR", "50, FAIR", "49, POOR", "38, POOR", "0, POOR"})
    void usesTheConfiguredBands(int score, RoastGrade expected) {
        assertThat(resolver.resolve(score)).isEqualTo(expected);
    }

    @Test
    void refusesBandsOutOfOrder() {
        RoastEvaluationProperties properties = new RoastEvaluationProperties();
        properties.setGoodGradeMin(90);

        assertThatThrownBy(() -> new RoastGradeResolver(properties)).isInstanceOf(IllegalStateException.class);
    }
}
