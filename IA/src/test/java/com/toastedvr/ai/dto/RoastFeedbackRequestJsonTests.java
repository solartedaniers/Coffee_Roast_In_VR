package com.toastedvr.ai.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toastedvr.ai.domain.KnowledgeLevel;
import com.toastedvr.ai.domain.RoastingResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

@JsonTest
class RoastFeedbackRequestJsonTests {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void readsTheSessionSentByTheBackend() throws Exception {
        String json = """
            {
              "result": "PERFECT",
              "qualityScore": 92,
              "chargeTemperature": 190.0,
              "targetTemperature": 200.0,
              "finalTemperature": 201.5,
              "totalDurationSeconds": 780,
              "firstCrackReached": true,
              "developmentTimeSeconds": 156,
              "knowledgeLevel": "BEGINNER"
            }
            """;

        RoastFeedbackRequest request = objectMapper.readValue(json, RoastFeedbackRequest.class);

        assertThat(request.getResult()).isEqualTo(RoastingResult.PERFECT);
        assertThat(request.getQualityScore()).isEqualTo(92);
        assertThat(request.getChargeTemperature()).isEqualTo(190.0);
        assertThat(request.getTargetTemperature()).isEqualTo(200.0);
        assertThat(request.getFinalTemperature()).isEqualTo(201.5);
        assertThat(request.getTotalDurationSeconds()).isEqualTo(780);
        assertThat(request.isFirstCrackReached()).isTrue();
        assertThat(request.getDevelopmentTimeSeconds()).isEqualTo(156);
        assertThat(request.getKnowledgeLevel()).isEqualTo(KnowledgeLevel.BEGINNER);
    }

    @Test
    void acceptsMissingOptionalValues() throws Exception {
        String json = """
            {"result": "RAW", "qualityScore": 20, "targetTemperature": 210.0,
             "finalTemperature": 180.0, "totalDurationSeconds": 500, "firstCrackReached": false}
            """;

        RoastFeedbackRequest request = objectMapper.readValue(json, RoastFeedbackRequest.class);

        assertThat(request.getChargeTemperature()).isNull();
        assertThat(request.getDevelopmentTimeSeconds()).isNull();
        assertThat(request.getKnowledgeLevel()).isNull();
    }
}
