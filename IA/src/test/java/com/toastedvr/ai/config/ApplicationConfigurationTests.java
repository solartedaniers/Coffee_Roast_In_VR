package com.toastedvr.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

// Revisa los valores por defecto de application.yml sin levantar el contexto:
// deben ser los mismos que el backend tenía fijos en el código.
class ApplicationConfigurationTests {

    private final Properties properties = loadApplicationYaml();

    @Test
    void listensOnlyOnLoopbackByDefault() {
        assertThat(properties.getProperty("server.address")).isEqualTo("${AI_SERVER_ADDRESS:127.0.0.1}");
        assertThat(properties.getProperty("server.port")).isEqualTo("${AI_SERVER_PORT:8082}");
    }

    @Test
    void keepsTheOllamaGenerationDefaults() {
        assertThat(properties.getProperty("ollama.model")).isEqualTo("${OLLAMA_FEEDBACK_MODEL:phi4-mini}");
        assertThat(properties.getProperty("ollama.connect-timeout-millis")).isEqualTo("${OLLAMA_CONNECT_TIMEOUT_MILLIS:5000}");
        assertThat(properties.getProperty("ollama.read-timeout-millis")).isEqualTo("${OLLAMA_READ_TIMEOUT_MILLIS:120000}");
        assertThat(properties.getProperty("ollama.keep-alive")).isEqualTo("${OLLAMA_KEEP_ALIVE:10m}");
        assertThat(properties.getProperty("ollama.temperature")).isEqualTo("${OLLAMA_TEMPERATURE:0.4}");
        assertThat(properties.getProperty("ollama.num-predict")).isEqualTo("${OLLAMA_NUM_PREDICT:240}");
    }

    @Test
    void keepsTheRagDefaults() {
        assertThat(properties.getProperty("rag.query.top-k")).isEqualTo("${RAG_TOP_K:4}");
        assertThat(properties.getProperty("rag.chunk.size-chars")).isEqualTo("${RAG_CHUNK_SIZE_CHARS:700}");
        assertThat(properties.getProperty("rag.chunk.overlap-chars")).isEqualTo("${RAG_CHUNK_OVERLAP_CHARS:100}");
    }

    // Mismos valores que usaba el backend, para reutilizar el índice existente.
    @Test
    void keepsTheSamePgvectorTable() {
        assertThat(properties.getProperty("spring.ai.vectorstore.pgvector.table-name")).isEqualTo("rag_document_chunks");
        assertThat(properties.getProperty("spring.ai.vectorstore.pgvector.dimensions")).isEqualTo("768");
        assertThat(properties.getProperty("spring.ai.vectorstore.pgvector.index-type")).isEqualTo("HNSW");
        assertThat(properties.getProperty("spring.ai.vectorstore.pgvector.distance-type")).isEqualTo("COSINE_DISTANCE");
    }

    private static Properties loadApplicationYaml() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yml"));
        return factory.getObject();
    }
}
