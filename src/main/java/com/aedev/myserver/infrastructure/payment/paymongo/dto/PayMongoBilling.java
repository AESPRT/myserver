package com.aedev.myserver.infrastructure.payment.paymongo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PayMongoBilling(
        String email,
        String name,
        String phone
) {

}