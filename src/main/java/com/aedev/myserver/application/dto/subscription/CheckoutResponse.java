package com.aedev.myserver.application.dto.subscription;

import java.math.BigDecimal;

public record CheckoutResponse(
        String checkoutUrl,
        String referenceNumber,
        BigDecimal amount,
        CheckoutMetadata metadata
) {
}