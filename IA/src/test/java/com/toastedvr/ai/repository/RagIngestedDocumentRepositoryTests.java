package com.toastedvr.ai.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.toastedvr.ai.domain.RagIngestedDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
class RagIngestedDocumentRepositoryTests {

    @Autowired
    private RagIngestedDocumentRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void findsDocumentByFileName() {
        repository.save(new RagIngestedDocument("manual.pdf", "a".repeat(64), 12));

        assertThat(repository.findByFileName("manual.pdf"))
            .hasValueSatisfying(document -> assertThat(document.getChunkCount()).isEqualTo(12));
        assertThat(repository.findByFileName("other.pdf")).isEmpty();
    }

    // La tabla debe seguir llamándose igual que en el backend para reutilizar
    // los registros de ingesta que ya existen en la base de datos.
    @Test
    void keepsTheSameTableNameAsTheBackend() {
        repository.saveAndFlush(new RagIngestedDocument("manual.pdf", "a".repeat(64), 12));

        Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rag_ingested_documents", Integer.class);

        assertThat(rows).isEqualTo(1);
    }
}
