package com.toastedvr.toastedvr.backend.config;

import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

// Revisa los valores por defecto de application.yml sin levantar el contexto.
class ApplicationConfigurationTests {

    private final Properties properties = loadApplicationYaml();

    @Test
    void shouldRequireJwtSecretFromEnvironmentWithoutDefault() {
        assertThat(properties.getProperty("app.jwt.secret")).isEqualTo("${APP_JWT_SECRET}");
    }

    @Test
    void shouldKeepSqlLoggingDisabledByDefault() {
        assertThat(properties.getProperty("spring.jpa.show-sql")).isEqualTo("${JPA_SHOW_SQL:false}");
    }

    @Test
    void shouldReachTheAiServiceOnTheLocalHostByDefault() {
        assertThat(properties.getProperty("app.ai-service.base-url")).isEqualTo("${AI_SERVICE_BASE_URL:http://localhost:8082}");
    }

    @Test
    void shouldWaitForTheVoiceQuestionLongerThanTheAiServiceItself() {
        assertThat(properties.getProperty("app.ai-service.voice-read-timeout-millis"))
            .isEqualTo("${AI_SERVICE_VOICE_READ_TIMEOUT_MILLIS:30000}");
    }

    @Test
    void shouldWaitForTheAlertPackageLongerThanTheAiServiceItself() {
        assertThat(properties.getProperty("app.ai-service.alerts-read-timeout-millis"))
            .isEqualTo("${AI_SERVICE_ALERTS_READ_TIMEOUT_MILLIS:30000}");
    }

    @Test
    void shouldReachTheRoastSimulatorOnTheLoopbackByDefault() {
        assertThat(properties.getProperty("app.roast-simulator.base-url"))
            .isEqualTo("${ROAST_SIMULATOR_BASE_URL:http://127.0.0.1:8083}");
        assertThat(properties.getProperty("app.roast-simulator.read-timeout-millis"))
            .isEqualTo("${ROAST_SIMULATOR_READ_TIMEOUT_MILLIS:5000}");
    }

    private static Properties loadApplicationYaml() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yml"));
        return factory.getObject();
    }
}
