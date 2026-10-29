package com.toastedvr.toastedvr.backend.repository;

import com.toastedvr.toastedvr.backend.domain.RoastSessionTrace;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoastSessionTraceRepository extends JpaRepository<RoastSessionTrace, Long> {

    Optional<RoastSessionTrace> findBySessionId(Long sessionId);
}
