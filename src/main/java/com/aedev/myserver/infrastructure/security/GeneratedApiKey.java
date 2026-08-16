package com.aedev.myserver.infrastructure.security;

public record GeneratedApiKey(
        String token,
        String keyId,
        String hash
) {
}
