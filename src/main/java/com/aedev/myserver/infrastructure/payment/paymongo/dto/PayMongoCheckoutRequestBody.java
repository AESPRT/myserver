package com.aedev.myserver.infrastructure.payment.paymongo.dto;

public record PayMongoCheckoutRequestBody(
        PayMongoData data
) {
    public record PayMongoData(
            PayMongoCheckoutAttributes attributes
    ) {
    }

    public static PayMongoCheckoutRequestBody wrap(PayMongoCheckoutAttributes attributes) {
        return new PayMongoCheckoutRequestBody(new PayMongoData(attributes));
    }
}