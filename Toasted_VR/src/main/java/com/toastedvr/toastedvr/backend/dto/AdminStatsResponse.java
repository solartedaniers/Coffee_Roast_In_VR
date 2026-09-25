package com.toastedvr.toastedvr.backend.dto;

import java.util.Map;

// averageScore y averageScoreByLevel (RF020) llevan un decimal y son null si
// no hay sesiones; las sesiones sin nivel se agrupan en la clave NOT_SET.
public record AdminStatsResponse(
    long totalUsers,
    long activeUsers,
    long blockedUsers,
    long adminUsers,
    Map<String, Long> knowledgeLevelCounts,
    long totalSessions,
    Map<String, Long> sessionResultCounts,
    Double averageScore,
    Map<String, Double> averageScoreByLevel
) {
}
