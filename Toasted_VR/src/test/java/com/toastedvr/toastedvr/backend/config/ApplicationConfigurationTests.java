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

    private static Properties loadApplicationYaml() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yml"));
        return factory.getObject();
    }
}
