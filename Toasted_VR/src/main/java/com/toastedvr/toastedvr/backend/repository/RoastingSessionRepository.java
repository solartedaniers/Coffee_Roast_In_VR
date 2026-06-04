package com.toastedvr.toastedvr.backend.repository;

import com.toastedvr.toastedvr.backend.domain.RoastingSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoastingSessionRepository extends JpaRepository<RoastingSession, Long> {
}
