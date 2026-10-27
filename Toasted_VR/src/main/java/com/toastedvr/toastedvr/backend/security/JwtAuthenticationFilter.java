package com.toastedvr.toastedvr.backend.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import com.toastedvr.toastedvr.backend.exception.ErrorCode;
import com.toastedvr.toastedvr.backend.service.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.lang.NonNull;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_FAILURE_REASON = "authFailureReason";
    public static final String AUTH_FAILURE_CODE = "authFailureCode";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;
    public JwtAuthenticationFilter(
        JwtService jwtService,
        CustomUserDetailsService userDetailsService,
        TokenBlacklistService tokenBlacklistService
    ) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorizationHeader.substring(7);

        try {
            if (tokenBlacklistService.isBlacklisted(token)) {
                request.setAttribute(AUTH_FAILURE_REASON, "Blacklisted token");
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            Long userId = jwtService.extractUserId(token);

            if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserPrincipal principal = (UserPrincipal) userDetailsService.loadUserById(userId);

                if (!principal.isEnabled()) {
                    markAccountBlocked(request);
                } else if (principal.wasIssuedBeforeSessionInvalidation(jwtService.getIssuedAt(token))) {
                    markFailure(request, "Revoked session", ErrorCode.SESSION_REVOKED);
                } else if (jwtService.isTokenValid(token, principal)) {
                    UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (ExpiredJwtException exception) {
            // Un token vencido de una cuenta bloqueada se informa como bloqueo,
            // para que el cliente no intente renovar la sesión.
            if (isBlockedUser(exception.getClaims().getSubject())) {
                markAccountBlocked(request);
            } else {
                request.setAttribute(AUTH_FAILURE_REASON, "Expired token");
            }
            SecurityContextHolder.clearContext();
        } catch (JwtException | IllegalArgumentException ignored) {
            request.setAttribute(AUTH_FAILURE_REASON, "Invalid token");
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private void markAccountBlocked(HttpServletRequest request) {
        markFailure(request, "Blocked account", ErrorCode.ACCOUNT_BLOCKED);
    }

    private void markFailure(HttpServletRequest request, String reason, ErrorCode code) {
        request.setAttribute(AUTH_FAILURE_REASON, reason);
        request.setAttribute(AUTH_FAILURE_CODE, code);
        SecurityContextHolder.clearContext();
    }

    private boolean isBlockedUser(String subject) {
        try {
            return !userDetailsService.loadUserById(Long.valueOf(subject)).isEnabled();
        } catch (UsernameNotFoundException | IllegalArgumentException exception) {
            return false;
        }
    }
}
