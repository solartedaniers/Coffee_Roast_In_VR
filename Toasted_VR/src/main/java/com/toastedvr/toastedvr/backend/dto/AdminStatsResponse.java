package com.toastedvr.toastedvr.backend.dto;

import java.util.Map;

public record AdminStatsResponse(
    long totalUsers,
    long activeUsers,
    long blockedUsers,
    long adminUsers,
    Map<String, Long> knowledgeLevelCounts,
    long totalSessions,
    Map<String, Long> sessionResultCounts
) {
}
