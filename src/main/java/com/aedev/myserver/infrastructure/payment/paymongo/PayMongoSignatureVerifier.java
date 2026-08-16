package com.aedev.myserver.infrastructure.payment.paymongo;

import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Verifies the "Paymongo-Signature" header PayMongo attaches to webhook
 * requests.
 * <p>
 * Header format:
 *   t=1701234567,te=abcdef123...,li=fedcba987...
 * <p>
 * - t  = unix timestamp the event was sent
 * - te = HMAC-SHA256 signature computed with the TEST webhook secret
 * - li = HMAC-SHA256 signature computed with the LIVE webhook secret
 * <p>
 * The signed payload is "{timestamp}.{raw_request_body}" -- NOT the body
 * alone. This is why signature verification must happen against the raw
 * bytes of the request before any JSON deserialization.
 * <p>
 * A valid HMAC alone does not prevent replay: a captured request (e.g.,
 * from a log, proxy, or browser dev tools) would still produce a valid
 * signature no matter how old it is. The webhook event-id uniqueness
 * constraint (see ProcessedWebhookEvent) already blocks a raw replay of
 * the same event from re-applying business effects, but rejecting stale
 * timestamps here closes the gap earlier, at the transport layer, rather
 * than relying solely on that downstream check.
 */
@Component
public class PayMongoSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(PayMongoSignatureVerifier.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /**
     * Maximum age of a webhook request before it's rejected as stale,
     * regardless of signature validity. 5 minutes covers normal delivery
     * latency and retry backoff without leaving a meaningfully exploitable
     * replay window.
     */
    private static final Duration TOLERANCE = Duration.ofMinutes(5);

    public boolean verify(String rawBody, String signatureHeader, String webhookSecret, boolean liveMode) {
        if (rawBody == null || signatureHeader == null || webhookSecret == null) {
            return false;
        }

        Map<String, String> parts = parseSignatureHeader(signatureHeader);
        String timestamp = parts.get("t");
        String providedSignature = liveMode ? parts.get("li") : parts.get("te");

        if (timestamp == null || providedSignature == null) {
            return false;
        }

        if (!isWithinTolerance(timestamp)) {
            log.warn(
                    "PayMongo webhook rejected: timestamp outside tolerance. " +
                            "timestamp={}, liveMode={}",
                    timestamp,
                    liveMode
            );
            return false;
        }

        String signedPayload = timestamp + "." + rawBody;
        String expectedSignature = hmacSha256Hex(signedPayload, webhookSecret);

        boolean valid = constantTimeEquals(
                expectedSignature,
                providedSignature
        );

        if (!valid) {
            log.warn(
                    "PayMongo webhook signature mismatch. " +
                            "mode={}, timestamp={}",
                    liveMode ? "LIVE" : "TEST",
                    timestamp
            );
        } else {
            log.info(
                    "PayMongo webhook signature verified. mode={}",
                    liveMode ? "LIVE" : "TEST"
            );
        }

        return valid;
    }

    private boolean isWithinTolerance(String timestampHeader) {
        long timestampSeconds;
        try {
            timestampSeconds = Long.parseLong(timestampHeader);
        } catch (NumberFormatException e) {
            return false;
        }

        long nowSeconds = System.currentTimeMillis() / 1000;
        long ageSeconds = Math.abs(nowSeconds - timestampSeconds);

        return ageSeconds <= TOLERANCE.getSeconds();
    }

    /** PayMongo's real header only ever has 3 segments (t, te, li). */
    private static final int MAX_SIGNATURE_HEADER_SEGMENTS = 10;

    private Map<String, String> parseSignatureHeader(String header) {
        Map<String, String> parts = new HashMap<>();
        String[] segments = header.split(",", MAX_SIGNATURE_HEADER_SEGMENTS + 1);
        if (segments.length > MAX_SIGNATURE_HEADER_SEGMENTS) {
            log.warn("Rejected PayMongo webhook: signature header has an unexpectedly high segment count");
            return parts;
        }
        for (String segment : segments) {
            String[] kv = segment.split("=", 2);
            if (kv.length == 2) {
                parts.put(kv[0].trim(), kv[1].trim());
            }
        }
        return parts;
    }

    private String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC signature", e);
        }
    }

    /** Avoids timing attacks on signature comparison. */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8)
        );
    }
}