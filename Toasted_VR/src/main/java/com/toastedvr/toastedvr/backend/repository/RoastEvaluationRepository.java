package com.toastedvr.toastedvr.backend.repository;

import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.domain.RoastEvaluation;
import com.toastedvr.toastedvr.backend.domain.RoastEvaluationStatus;
import com.toastedvr.toastedvr.backend.domain.Role;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoastEvaluationRepository extends JpaRepository<RoastEvaluation, Long> {

    Optional<RoastEvaluation> findBySessionId(Long sessionId);

    List<RoastEvaluation> findBySessionIdIn(Collection<Long> sessionIds);

    // Sesiones que cuentan para el ranking de un nivel (RF017): con nota combinada y guardadas desde
    // la fecha configurada, de mejor a peor y, en empate, de la más antigua a la más nueva. Las
    // sesiones sin nivel no entran; enabled o role en null se tratan como activo y PLAYER, igual que User.
    @Query("""
        select e from RoastEvaluation e join fetch e.session s join fetch s.user u
        where e.status = :status
          and s.knowledgeLevel = :level
          and s.createdAt >= :since
          and (u.enabled is null or u.enabled = true)
          and (u.role is null or u.role <> :excludedRole)
        order by e.finalScore desc, s.createdAt asc, s.id asc
        """)
    List<RoastEvaluation> findRankingCandidates(
        @Param("status") RoastEvaluationStatus status,
        @Param("level") KnowledgeLevel level,
        @Param("since") LocalDateTime since,
        @Param("excludedRole") Role excludedRole
    );

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
