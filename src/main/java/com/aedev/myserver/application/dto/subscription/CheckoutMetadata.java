package com.aedev.myserver.application.dto.subscription;

import com.aedev.myserver.domain.enums.BillingCycle;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Structured representation of the PayMongo checkout metadata block.
 * Field names use @JsonProperty to preserve the exact snake_case keys
 * PayMongo expects and the original Node.js implementation sent --
 * changing these keys would silently break webhook metadata parsing
 * (Step 15) since PayMongo echoes metadata back verbatim in events.
 * <p>
 * referenceNumber is included so WebhookProcessingService can look up
 * the Transaction row by reference number if the checkout_session_id
 * was never attached to it (e.g., the DB write that attaches it failed
 * after PayMongo already created the session) -- see Step 20's
 * consistency-check fix in TransactionRecordService/WebhookProcessingService.
 */
public record CheckoutMetadata(

        @JsonProperty("user_id")
        String userId,

        @JsonProperty("type")
        String type,

        @JsonProperty("package_id")
        String packageId,

        @JsonProperty("billing_cycle")
        BillingCycle billingCycle,

        @JsonProperty("customer_email")
        String customerEmail,

        @JsonProperty("customer_name")
        String customerName,

        @JsonProperty("customer_phone")
        String customerPhone,

        @JsonProperty("reference_number")
        String referenceNumber
) {
    private static final String SUBSCRIPTION_TYPE = "SUBSCRIPTION";

    public static CheckoutMetadata of(
            String userId,
            String packageId,
            BillingCycle billingCycle,
            String customerEmail,
            String customerName,
            String customerPhone,
            String referenceNumber
    ) {
        return new CheckoutMetadata(
                userId, SUBSCRIPTION_TYPE, packageId, billingCycle, customerEmail, customerName, customerPhone, referenceNumber);
    }
}