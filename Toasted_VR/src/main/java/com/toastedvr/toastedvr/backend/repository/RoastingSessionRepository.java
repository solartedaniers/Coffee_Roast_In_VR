package com.toastedvr.toastedvr.backend.repository;

import com.toastedvr.toastedvr.backend.domain.RoastingResult;
import com.toastedvr.toastedvr.backend.domain.RoastingSession;
import com.toastedvr.toastedvr.backend.dto.SessionHistorySummaryResponse;
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
}
