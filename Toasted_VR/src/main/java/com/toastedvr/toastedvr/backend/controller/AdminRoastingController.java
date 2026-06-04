package com.toastedvr.toastedvr.backend.controller;

import com.toastedvr.toastedvr.backend.dto.AdminRoastingSessionResponse;
import com.toastedvr.toastedvr.backend.service.AdminRoastingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminRoastingController {

    private final AdminRoastingService adminRoastingService;

    public AdminRoastingController(AdminRoastingService adminRoastingService) {
        this.adminRoastingService = adminRoastingService;
    }

    @GetMapping("/roasting-sessions")
    public Page<AdminRoastingSessionResponse> listAllSessions(
        @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        return adminRoastingService.listAllSessions(pageable);
    }
}
