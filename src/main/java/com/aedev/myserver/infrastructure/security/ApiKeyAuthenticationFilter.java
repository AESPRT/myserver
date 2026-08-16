package com.aedev.myserver.infrastructure.security;

import com.aedev.myserver.domain.entity.Subscription;
import com.aedev.myserver.domain.repository.SubscriptionRepository;
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
import java.util.Optional;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-API-Key";

    private final SubscriptionRepository subscriptionRepository;
    private final ApiKeyService apiKeyService;

    public ApiKeyAuthenticationFilter(SubscriptionRepository subscriptionRepository, ApiKeyService apiKeyService) {
        this.subscriptionRepository = subscriptionRepository;
        this.apiKeyService = apiKeyService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String rawKey = request.getHeader(HEADER_NAME);

        if (rawKey != null && !rawKey.isBlank()) {
            int dotIndex = rawKey.indexOf('.');
            if (dotIndex > 0) {
                String keyId = rawKey.substring(0, dotIndex);
                String expectedHash = apiKeyService.hash(rawKey);

                Optional<Subscription> subscription = subscriptionRepository.findByApiKeyId(keyId);
                if (subscription.isPresent() && subscription.get().getApiKeyHash().equals(expectedHash)) {
                    var authentication = new UsernamePasswordAuthenticationToken(
                            subscription.get().getUserId(), null, List.of()
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}