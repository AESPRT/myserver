package com.aedev.myserver.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates requests carrying a static, app-wide key in X-App-Key.
 * Distinct from ApiKeyAuthenticationFilter (X-API-Key), which validates
 * per-subscription keys tied to a Subscription row -- these are two
 * unrelated concepts and must not share a header name.
 * <p>
 * Guards /api/v1/audio and /sms/send. The SMS endpoint is gated here
 * specifically because Semaphore bills per message sent -- an
 * unauthenticated POST /sms/send would let anyone run up the account's
 * SMS bill with no cost to themselves.
 */
@Component
public class AppApiKeyFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-App-Key";

    private static final List<String> PROTECTED_PATH_PREFIXES = List.of(
            "/api/v1/audio",
            "/api/v1/sms",
            "/api/v1/rag"
    );

    private final AppApiKeyProperties appApiKeyProperties;

    public AppApiKeyFilter(AppApiKeyProperties appApiKeyProperties) {
        this.appApiKeyProperties = appApiKeyProperties;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        boolean isProtected = PROTECTED_PATH_PREFIXES.stream()
                .anyMatch(prefix -> request.getRequestURI().startsWith(prefix));

        if (!isProtected) {
            filterChain.doFilter(request, response);
            return;
        }

        String providedKey = request.getHeader(HEADER_NAME);
        String expectedKey = appApiKeyProperties.apiKey();

        boolean valid = providedKey != null
                && expectedKey != null
                && !expectedKey.isBlank()
                && constantTimeEquals(providedKey, expectedKey);

        if (!valid) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Invalid or missing X-App-Key\"}");
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken("app-client", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    /**
     * Plain .equals() on secrets is vulnerable to timing attacks -- an
     * attacker can measure response time differences to guess the key
     * byte-by-byte. MessageDigest.isEqual runs in constant time
     * regardless of where the strings first differ.
     */
    private boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(
                a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                b.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }
}