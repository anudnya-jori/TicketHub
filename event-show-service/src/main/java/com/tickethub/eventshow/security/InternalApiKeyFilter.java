package com.tickethub.eventshow.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * /internal/** is service-to-service only. Even though the gateway does not route it, a service must not trust
 * "being on the internal network": every internal call has to carry the shared key (header X-Internal-Api-Key).
 */
public class InternalApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Api-Key";

    private final byte[] expected;
    private final SecurityErrorHandlers errors;

    public InternalApiKeyFilter(String apiKey, SecurityErrorHandlers errors) {
        this.expected = apiKey.getBytes(StandardCharsets.UTF_8);
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        boolean ok = supplied != null
                && MessageDigest.isEqual(expected, supplied.getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            errors.write(request, response, HttpStatus.UNAUTHORIZED, "INVALID_INTERNAL_KEY",
                    "Internal endpoints require a valid service key.");
            return;
        }
        chain.doFilter(request, response);
    }
}
