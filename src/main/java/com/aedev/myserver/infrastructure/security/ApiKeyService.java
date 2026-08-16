package com.aedev.myserver.infrastructure.security;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class ApiKeyService {

    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyResult generate() {

        String keyId = "key_" + UUID.randomUUID()
                .toString()
                .replace("-", "");

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);

        String secret = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        /*
         * The customer receives this value:
         *
         * keyId.secret
         *
         * Example:
         * key_abc123.xxxxxxxxxxxxxxxxx
         */
        String apiKey = keyId + "." + secret;

        String hash = hash(apiKey);

        return new ApiKeyResult(
                keyId,
                apiKey,
                hash
        );
    }

    public String hash(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    apiKey.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to hash API key",
                    e
            );
        }
    }

    public record ApiKeyResult(
            String keyId,
            String apiKey,
            String hash
    ) {
    }
}