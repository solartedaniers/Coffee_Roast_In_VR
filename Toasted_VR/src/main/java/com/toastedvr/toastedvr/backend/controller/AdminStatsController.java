package com.toastedvr.toastedvr.backend.controller;

import com.toastedvr.toastedvr.backend.dto.AdminStatsResponse;
import com.toastedvr.toastedvr.backend.service.AdminStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    public AdminStatsController(AdminStatsService adminStatsService) {
        this.adminStatsService = adminStatsService;
    }

    @GetMapping("/stats")
    public AdminStatsResponse getStats() {
        return adminStatsService.getStats();
    }
}
