package com.toastedvr.toastedvr.backend.repository;

import com.toastedvr.toastedvr.backend.domain.RoastEvaluation;
import com.toastedvr.toastedvr.backend.domain.RoastEvaluationStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoastEvaluationRepository extends JpaRepository<RoastEvaluation, Long> {

    Optional<RoastEvaluation> findBySessionId(Long sessionId);

    // Las que ya pueden intentarse, de la que más espera a la que menos.
    @Query("""
        select e from RoastEvaluation e join fetch e.session
        where e.status = :status and e.nextAttemptAt <= :now
        order by e.nextAttemptAt asc, e.id asc
        """)
    List<RoastEvaluation> findDue(
        @Param("status") RoastEvaluationStatus status,
        @Param("now") LocalDateTime now,
        Pageable pageable
    );
}
