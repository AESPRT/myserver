package com.aedev.myserver.infrastructure.payment.paymongo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PayMongoLineItem(
        String name,
        long amount,
        String currency,
        int quantity
) {
    @JsonProperty("amount")
    public long amount() {
        return amount;
    }
}