package com.aedev.myserver.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum PaymentMethodType {

    CARD("card"),
    GCASH("gcash"),
    PAYMAYA("paymaya"),
    QRPH("qrph");

    private final String value;

    PaymentMethodType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static PaymentMethodType fromValue(String value) {
        return Arrays.stream(values())
                .filter(type -> type.value.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException("Unsupported payment method: " + value)
                );
    }

    @Override
    public String toString() {
        return value;
    }
}