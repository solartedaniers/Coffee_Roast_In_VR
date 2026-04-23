package com.toastedvr.toastedvr.backend.controller;

import com.toastedvr.toastedvr.backend.domain.Role;
import com.toastedvr.toastedvr.backend.dto.UpdateUserRoleRequest;
import com.toastedvr.toastedvr.backend.dto.UpdateUserStatusRequest;
import com.toastedvr.toastedvr.backend.dto.UserAdminResponse;
import com.toastedvr.toastedvr.backend.dto.UserSummaryResponse;
import com.toastedvr.toastedvr.backend.security.UserPrincipal;
import com.toastedvr.toastedvr.backend.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public Page<UserSummaryResponse> listUsers(
        @RequestParam(required = false) String name,
        @RequestParam(required = false) String email,
        @RequestParam(required = false) Boolean enabled,
        @RequestParam(required = false) Role role,
        @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        return adminUserService.listUsers(name, email, enabled, role, pageable);
    }

    @GetMapping("/{id}")
    public UserAdminResponse getUserById(@PathVariable Long id) {
        return adminUserService.getUserById(id);
    }

    @PatchMapping("/{id}/status")
    public UserAdminResponse updateStatus(
        @PathVariable Long id,
        @Valid @RequestBody UpdateUserStatusRequest request,
        @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        return adminUserService.updateStatus(id, currentUser.getId(), request);
    }

    @PatchMapping("/{id}/role")
    public UserAdminResponse updateRole(
        @PathVariable Long id,
        @Valid @RequestBody UpdateUserRoleRequest request,
        @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        return adminUserService.updateRole(id, currentUser.getId(), request);
    }
}
