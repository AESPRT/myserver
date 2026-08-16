package com.aedev.myserver.infrastructure.payment.paymongo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PayMongoCheckoutResponse(
        PayMongoResponseData data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PayMongoResponseData(
            String id,
            PayMongoResponseAttributes attributes
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PayMongoResponseAttributes(
            @JsonProperty("checkout_url") String checkoutUrl
    ) {
    }
}