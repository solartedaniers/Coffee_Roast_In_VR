package com.toastedvr.toastedvr.backend.service;

import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.domain.Role;
import com.toastedvr.toastedvr.backend.domain.RoastingResult;
import com.toastedvr.toastedvr.backend.dto.AdminStatsResponse;
import com.toastedvr.toastedvr.backend.repository.RoastingSessionRepository;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AdminStatsService {

    // Misma clave que usa knowledgeLevelCounts para "sin nivel".
    private static final String NO_LEVEL_KEY = "NOT_SET";

    private final UserRepository userRepository;
    private final RoastingSessionRepository roastingSessionRepository;

    public AdminStatsService(
        UserRepository userRepository,
        RoastingSessionRepository roastingSessionRepository
    ) {
        this.userRepository = userRepository;
        this.roastingSessionRepository = roastingSessionRepository;
    }

    public AdminStatsResponse getStats() {
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByEnabled(true);
        long blockedUsers = userRepository.countByEnabled(false);
        long adminUsers = userRepository.countByRole(Role.ADMIN);

        Map<String, Long> knowledgeLevelCounts = new LinkedHashMap<>();
        for (KnowledgeLevel level : KnowledgeLevel.values()) {
            knowledgeLevelCounts.put(level.name(), userRepository.countByKnowledgeLevel(level));
        }
        knowledgeLevelCounts.put("NOT_SET", userRepository.countByKnowledgeLevelIsNull());

        long totalSessions = roastingSessionRepository.count();

        Map<String, Long> sessionResultCounts = new LinkedHashMap<>();
        for (RoastingResult result : RoastingResult.values()) {
            sessionResultCounts.put(result.name(), roastingSessionRepository.countByResult(result));
        }

        return new AdminStatsResponse(
            totalUsers,
            activeUsers,
            blockedUsers,
            adminUsers,
            knowledgeLevelCounts,
            totalSessions,
            sessionResultCounts,
            oneDecimal(roastingSessionRepository.averageQualityScore()),
            averageScoreByLevel()
        );
    }

    // Siempre las cuatro claves, en orden; un nivel sin sesiones queda en null.
    private Map<String, Double> averageScoreByLevel() {
        Map<String, Double> averages = new LinkedHashMap<>();
        for (KnowledgeLevel level : KnowledgeLevel.values()) {
            averages.put(level.name(), null);
        }
        averages.put(NO_LEVEL_KEY, null);
        roastingSessionRepository.averageQualityScoreByLevel().forEach(row -> averages.put(
            row.getLevel() == null ? NO_LEVEL_KEY : row.getLevel().name(),
            oneDecimal(row.getAverage())
        ));
        return averages;
    }

    private Double oneDecimal(Double value) {
        return value == null ? null : Math.round(value * 10) / 10.0;
    }
}
