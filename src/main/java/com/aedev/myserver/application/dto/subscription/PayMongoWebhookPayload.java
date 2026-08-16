package com.aedev.myserver.application.dto.subscription;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PayMongoWebhookPayload(

        @JsonProperty("data")
        EventEnvelope data

) {

    /**
     * Top-level PayMongo event envelope.
     * <p>
     * Example:
     * <p>
     * {
     *   "data": {
     *     "id": "evt_xxx",
     *     "type": "event",
     *     "attributes": {
     *       ...
     *     }
     *   }
     * }
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EventEnvelope(

            @JsonProperty("id")
            String eventId,

            @JsonProperty("type")
            String eventResourceType,

            @JsonProperty("attributes")
            EventAttributes attributes

    ) {
    }

    /**
     * PayMongo event attributes.
     * <p>
     * The "data" field contains the actual resource associated
     * with the event.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EventAttributes(

            @JsonProperty("type")
            String type,

            /*
             * PayMongo uses "livemode" in webhook payloads.
             */
            @JsonProperty("livemode")
            boolean liveMode,

            @JsonProperty("data")
            ResourceEnvelope resourceData

    ) {
    }

    /**
     * Resource envelope.
     * <p>
     * This can represent different PayMongo resources:
     * <p>
     * - checkout_session
     * - payment
     * - invoice
     * - etc.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResourceEnvelope(

            @JsonProperty("id")
            String resourceId,

            @JsonProperty("type")
            String resourceType,

            @JsonProperty("attributes")
            ResourceAttributes attributes

    ) {
    }

    /**
     * Resource attributes.
     * <p>
     * The available fields depend on the PayMongo resource.
     * <p>
     * For checkout_session.payment.paid:
     * <p>
     *     metadata
     *     payments[]
     * <p>
     * For payment events:
     * <p>
     *     amount
     *     status
     *     metadata
     * <p>
     * Unknown fields are intentionally ignored so PayMongo can
     * add fields without breaking webhook deserialization.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResourceAttributes(

            @JsonProperty("amount")
            Long amount,

            @JsonProperty("status")
            String status,

            @JsonProperty("metadata")
            Map<String, String> metadata,

            @JsonProperty("payments")
            List<Payment> payments

    ) {
    }

    /**
     * Payment resource embedded inside a Checkout Session.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payment(

            @JsonProperty("id")
            String id,

            @JsonProperty("type")
            String type,

            @JsonProperty("attributes")
            PaymentAttributes attributes

    ) {
    }

    /**
     * Attributes of an embedded payment.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentAttributes(

            @JsonProperty("amount")
            Long amount,

            @JsonProperty("status")
            String status,

            @JsonProperty("currency")
            String currency,

            @JsonProperty("fee")
            Long fee,

            @JsonProperty("net_amount")
            Long netAmount,

            @JsonProperty("metadata")
            Map<String, String> metadata

    ) {
    }
}