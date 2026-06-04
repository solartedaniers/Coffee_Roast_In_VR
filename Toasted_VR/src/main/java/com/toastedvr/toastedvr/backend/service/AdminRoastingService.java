package com.toastedvr.toastedvr.backend.service;

import com.toastedvr.toastedvr.backend.domain.RoastingSession;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.dto.AdminRoastingSessionResponse;
import com.toastedvr.toastedvr.backend.repository.RoastingSessionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class AdminRoastingService {

    private final RoastingSessionRepository roastingSessionRepository;

    public AdminRoastingService(RoastingSessionRepository roastingSessionRepository) {
        this.roastingSessionRepository = roastingSessionRepository;
    }

    public Page<AdminRoastingSessionResponse> listAllSessions(Pageable pageable) {
        return roastingSessionRepository.findAll(pageable).map(this::toResponse);
    }

    private AdminRoastingSessionResponse toResponse(RoastingSession session) {
        User user = session.getUser();
        return new AdminRoastingSessionResponse(
            session.getId(),
            user.getName(),
            user.getEmail(),
            user.getUsername(),
            session.getTargetTemperature(),
            session.getTotalDurationSeconds(),
            session.getFinalTemperature(),
            session.getResult().name(),
            session.getQualityScore(),
            session.isFirstCrackReached(),
            session.getCreatedAt()
        );
    }
}
