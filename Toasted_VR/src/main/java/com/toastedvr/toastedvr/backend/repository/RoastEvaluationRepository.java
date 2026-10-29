package com.toastedvr.toastedvr.backend.repository;

import com.toastedvr.toastedvr.backend.domain.RoastEvaluation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoastEvaluationRepository extends JpaRepository<RoastEvaluation, Long> {

    Optional<RoastEvaluation> findBySessionId(Long sessionId);
}
