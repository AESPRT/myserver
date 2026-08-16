package com.aedev.myserver.infrastructure.payment.paymongo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record PayMongoCheckoutAttributes(
        @JsonProperty("billing") PayMongoBilling billing,
        @JsonProperty("cancel_url") String cancelUrl,
        @JsonProperty("success_url") String successUrl,
        @JsonProperty("line_items") List<PayMongoLineItem> lineItems,
        @JsonProperty("payment_method_types") List<String> paymentMethodTypes,
        String description,
        Map<String, Object> metadata
) {
}