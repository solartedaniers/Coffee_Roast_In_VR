package com.toastedvr.ai.repository;

import com.toastedvr.ai.domain.RagIngestedDocument;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RagIngestedDocumentRepository extends JpaRepository<RagIngestedDocument, Long> {

    Optional<RagIngestedDocument> findByFileName(String fileName);
}
