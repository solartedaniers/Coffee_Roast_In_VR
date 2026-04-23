package com.toastedvr.toastedvr.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toastedvr.toastedvr.backend.dto.ApiErrorResponse;
import com.toastedvr.toastedvr.backend.service.AuditService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(AuditService auditService, ObjectMapper objectMapper) {
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException authException
    ) throws IOException, ServletException {
        String detail = (String) request.getAttribute(JwtAuthenticationFilter.AUTH_FAILURE_REASON);
        String failureDetail = detail != null ? detail : "Authentication required";

        auditService.logUnauthorizedAccess("anonymous", request.getRequestURI(), failureDetail);
        writeResponse(response, request.getRequestURI(), HttpStatus.UNAUTHORIZED, "No autorizado.");
    }

    private void writeResponse(
        HttpServletResponse response,
        String path,
        HttpStatus status,
        String message
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
            response.getOutputStream(),
            new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path
            )
        );
    }
}
