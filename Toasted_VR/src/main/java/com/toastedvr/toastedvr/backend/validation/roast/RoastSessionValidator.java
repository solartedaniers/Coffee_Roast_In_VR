package com.toastedvr.toastedvr.backend.validation.roast;

import com.toastedvr.toastedvr.backend.dto.SaveSessionRequest;
import com.toastedvr.toastedvr.backend.exception.RoastSessionRejectedException;
import com.toastedvr.toastedvr.backend.service.AuditService;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class RoastSessionValidator {

    private final List<RoastSessionRule> rules;
    private final AuditService auditService;

    public RoastSessionValidator(List<RoastSessionRule> rules, AuditService auditService) {
        this.rules = rules;
        this.auditService = auditService;
    }

    public void validate(Long userId, SaveSessionRequest request) {
        for (RoastSessionRule rule : rules) {
            Optional<RoastSessionViolation> violation = rule.findViolation(request);
            if (violation.isPresent()) {
                auditService.logRoastSessionRejected(userId, violation.get().rule(), violation.get().message());
                throw new RoastSessionRejectedException(violation.get().rule(), violation.get().message());
            }
        }
    }
}
