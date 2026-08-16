package com.aedev.myserver.infrastructure.payment.paymongo;

/**
 * Internal result of a successful PayMongo checkout session creation.
 * Kept separate from PayMongoCheckoutResponse (the raw API DTO) so the
 * rest of the app depends only on this stable shape, not PayMongo's
 * an envelope format.
 */
public record PayMongoCheckoutResult(
        String checkoutUrl,
        String checkoutSessionId
) {
}