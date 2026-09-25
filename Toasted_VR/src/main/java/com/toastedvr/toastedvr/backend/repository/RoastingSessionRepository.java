package com.toastedvr.toastedvr.backend.repository;

import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.domain.Role;
import com.toastedvr.toastedvr.backend.domain.RoastingResult;
import com.toastedvr.toastedvr.backend.domain.RoastingSession;
import com.toastedvr.toastedvr.backend.dto.SessionHistorySummaryResponse;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoastingSessionRepository extends JpaRepository<RoastingSession, Long> {

    long countByResult(RoastingResult result);

    Page<RoastingSession> findByUserId(Long userId, Pageable pageable);

    Page<RoastingSession> findByUserIdAndResult(Long userId, RoastingResult result, Pageable pageable);

    // Sin sesiones, max y avg llegan en null y count en 0.
    @Query("""
        select new com.toastedvr.toastedvr.backend.dto.SessionHistorySummaryResponse(
            max(s.qualityScore), avg(s.qualityScore), count(s))
        from RoastingSession s
        where s.user.id = :userId
        """)
    SessionHistorySummaryResponse summarizeByUserId(@Param("userId") Long userId);

    // Sesiones que cuentan para el ranking de un nivel, de mejor a peor y, en
    // empate, de la más antigua a la más nueva. Las sesiones sin nivel no
    // entran; enabled o role en null se tratan como activo y PLAYER, igual que User.
    @Query("""
        select s from RoastingSession s join fetch s.user u
        where s.knowledgeLevel = :level
          and (u.enabled is null or u.enabled = true)
          and (u.role is null or u.role <> :excludedRole)
        order by s.qualityScore desc, s.createdAt asc, s.id asc
        """)
    List<RoastingSession> findRankingCandidates(
        @Param("level") KnowledgeLevel level,
        @Param("excludedRole") Role excludedRole
    );
}
