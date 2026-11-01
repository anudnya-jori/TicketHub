package com.tickethub.notification.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

/**
 * Notification has no customer-facing API at all, so it needs no user login: only the service key on /internal/**.
 * Active when security.enabled=true.
 */
@Component
@ConditionalOnProperty(name = "security.enabled", havingValue = "true")
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private final byte[] expected;

    public InternalApiKeyFilter(@Value("${internal.api-key}") String apiKey) {
        this.expected = apiKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader("X-Internal-Api-Key");
        if (supplied == null || !MessageDigest.isEqual(expected, supplied.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write("{\"timestamp\":\"" + Instant.now() + "\",\"status\":401,\"error\":\"Unauthorized\","
                    + "\"code\":\"INVALID_INTERNAL_KEY\",\"message\":\"Internal endpoints require a valid service key.\","
                    + "\"path\":\"" + request.getRequestURI() + "\",\"details\":[]}");
            return;
        }
        chain.doFilter(request, response);
    }
}
