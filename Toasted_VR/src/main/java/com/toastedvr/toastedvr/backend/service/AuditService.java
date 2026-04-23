package com.toastedvr.toastedvr.backend.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private static final Logger AUDIT_LOGGER = LoggerFactory.getLogger("AUDIT_LOG");

    public void logUnauthorizedAccess(String subject, String path, String detail) {
        AUDIT_LOGGER.warn(
            "event=UNAUTHORIZED_ACCESS timestamp={} subject={} path={} detail={}",
            Instant.now(),
            subject,
            path,
            detail
        );
    }

    public void logRoleChange(Long authorId, Long targetUserId, String previousRole, String newRole) {
        AUDIT_LOGGER.info(
            "event=ROLE_CHANGE timestamp={} authorId={} targetUserId={} previousRole={} newRole={}",
            Instant.now(),
            authorId,
            targetUserId,
            previousRole,
            newRole
        );
    }

    public void logStatusChange(Long authorId, Long targetUserId, boolean enabled) {
        AUDIT_LOGGER.info(
            "event=USER_STATUS_CHANGE timestamp={} authorId={} targetUserId={} enabled={}",
            Instant.now(),
            authorId,
            targetUserId,
            enabled
        );
    }

    public void logSuccessfulLogin(Long userId, String username) {
        AUDIT_LOGGER.info(
            "event=LOGIN_SUCCESS timestamp={} userId={} username={}",
            Instant.now(),
            userId,
            username
        );
    }

    public void logLogout(Long userId, String username) {
        AUDIT_LOGGER.info(
            "event=LOGOUT timestamp={} userId={} username={}",
            Instant.now(),
            userId,
            username
        );
    }
}
