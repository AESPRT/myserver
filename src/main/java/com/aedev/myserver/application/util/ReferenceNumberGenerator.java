package com.aedev.myserver.application.util;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Generates transaction reference numbers. Replaces the original
 * TXN-RENTAL-${Date.now()} pattern, which risked collisions when two
 * requests landed in the same millisecond (see Step 1, problem #1).
 * <p>
 * A random UUID segment makes collision probability practically zero
 * without needing a DB round-trip or sequence lookup before insert --
 * the DB unique constraint on reference_number (Step 5) is still the
 * final safety net.
 */
@Component
public class ReferenceNumberGenerator {

    private static final String PREFIX = "TXN-RENTAL-";

    public String generate() {
        String uuidSegment = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();
        return PREFIX + System.currentTimeMillis() + "-" + uuidSegment;
    }
}