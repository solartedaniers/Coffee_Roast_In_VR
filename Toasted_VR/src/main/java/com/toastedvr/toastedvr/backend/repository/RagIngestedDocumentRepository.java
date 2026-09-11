package com.toastedvr.toastedvr.backend.repository;

import com.toastedvr.toastedvr.backend.domain.RagIngestedDocument;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RagIngestedDocumentRepository extends JpaRepository<RagIngestedDocument, Long> {

    Optional<RagIngestedDocument> findByFileName(String fileName);
}
