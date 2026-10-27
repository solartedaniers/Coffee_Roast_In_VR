package com.toastedvr.toastedvr.backend.repository;

import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.domain.RankingSnapshot;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RankingSnapshotRepository extends JpaRepository<RankingSnapshot, Long> {

    Optional<RankingSnapshot> findByUserIdAndKnowledgeLevel(Long userId, KnowledgeLevel knowledgeLevel);
}
