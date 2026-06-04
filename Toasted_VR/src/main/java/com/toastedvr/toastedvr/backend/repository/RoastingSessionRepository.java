package com.toastedvr.toastedvr.backend.repository;

import com.toastedvr.toastedvr.backend.domain.RoastingResult;
import com.toastedvr.toastedvr.backend.domain.RoastingSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoastingSessionRepository extends JpaRepository<RoastingSession, Long> {

    @EntityGraph(attributePaths = "user")
    Page<RoastingSession> findAll(Pageable pageable);

    long countByResult(RoastingResult result);
}
