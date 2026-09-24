package com.toastedvr.toastedvr.backend.service;

import com.toastedvr.toastedvr.backend.config.MessageResolver;
import com.toastedvr.toastedvr.backend.domain.Role;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.dto.ApiInstants;
import com.toastedvr.toastedvr.backend.dto.UpdateUserRoleRequest;
import com.toastedvr.toastedvr.backend.dto.UpdateUserStatusRequest;
import com.toastedvr.toastedvr.backend.dto.UserAdminResponse;
import com.toastedvr.toastedvr.backend.dto.UserSummaryResponse;
import com.toastedvr.toastedvr.backend.exception.ConflictException;
import com.toastedvr.toastedvr.backend.exception.ResourceNotFoundException;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import jakarta.transaction.Transactional;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final AuditService auditService;
    private final MessageResolver messages;

    public AdminUserService(UserRepository userRepository, AuditService auditService, MessageResolver messages) {
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.messages = messages;
    }

    public Page<UserSummaryResponse> listUsers(
        String name,
        String email,
        Boolean enabled,
        Role role,
        Pageable pageable
    ) {
        Pageable safePageable = Objects.requireNonNull(pageable, "pageable must not be null");
        Specification<User> specification = Specification.allOf(
            nameContains(normalizeFilter(name)),
            emailContains(normalizeFilter(email)),
            enabledEquals(enabled),
            roleEquals(role)
        );

        return userRepository.findAll(specification, safePageable)
            .map(this::toSummaryResponse);
    }

    public UserAdminResponse getUserById(Long id) {
        return toAdminResponse(findOrThrow(id), messages.get("admin.user.found"));
    }

    @Transactional
    public UserAdminResponse updateStatus(Long targetId, Long requesterId, UpdateUserStatusRequest request) {
        if (targetId.equals(requesterId) && Boolean.FALSE.equals(request.enabled())) {
            throw new ConflictException(messages.get("admin.user.cannotBlockSelf"));
        }

        User user = findOrThrow(targetId);
        boolean previousEnabled = user.isEnabled();

        if (Boolean.TRUE.equals(request.enabled())) {
            user.activate();
        } else {
            user.block();
            // Sin refresh token no puede renovar la sesión; el filtro JWT rechaza el access token vigente.
            user.clearRefreshToken();
        }

        auditService.logStatusChange(requesterId, user.getId(), previousEnabled, user.isEnabled());

        String messageKey = user.isEnabled() ? "admin.user.activated" : "admin.user.blocked";
        return toAdminResponse(user, messages.get(messageKey));
    }

    @Transactional
    public UserAdminResponse updateRole(Long targetId, Long requesterId, UpdateUserRoleRequest request) {
        if (targetId.equals(requesterId)) {
            throw new ConflictException(messages.get("admin.user.cannotChangeOwnRole"));
        }

        User user = findOrThrow(targetId);
        Role previousRole = user.getRole();
        user.assignRole(request.role());
        auditService.logRoleChange(requesterId, user.getId(), previousRole.name(), request.role().name());

        return toAdminResponse(user, messages.get("admin.user.roleUpdated", request.role()));
    }

    private @NonNull User findOrThrow(Long id) {
        User user = userRepository.findById(Objects.requireNonNull(id))
            .orElseThrow(() -> new ResourceNotFoundException(messages.get("admin.user.notFound")));

        return Objects.requireNonNull(user, "Resolved user must not be null.");
    }

    private String normalizeFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private Specification<User> nameContains(String name) {
        return (root, query, criteriaBuilder) ->
            name == null
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("name")),
                    "%" + name.toLowerCase() + "%"
                );
    }

    private Specification<User> emailContains(String email) {
        return (root, query, criteriaBuilder) ->
            email == null
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("email")),
                    "%" + email.toLowerCase() + "%"
                );
    }

    private Specification<User> enabledEquals(Boolean enabled) {
        return (root, query, criteriaBuilder) ->
            enabled == null
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.equal(root.get("enabled"), enabled);
    }

    private Specification<User> roleEquals(Role role) {
        return (root, query, criteriaBuilder) ->
            role == null
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.equal(root.get("role"), role);
    }

    private UserSummaryResponse toSummaryResponse(@NonNull User user) {
        return new UserSummaryResponse(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getUsername(),
            user.isEmailVerified(),
            user.isEnabled(),
            user.getRole(),
            ApiInstants.from(user.getCreatedAt()),
            user.getKnowledgeLevel()
        );
    }

    private UserAdminResponse toAdminResponse(@NonNull User user, String message) {
        return new UserAdminResponse(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getUsername(),
            user.isEmailVerified(),
            user.isEnabled(),
            user.getRole(),
            ApiInstants.from(user.getCreatedAt()),
            message
        );
    }
}