package com.aedev.myserver.infrastructure.sms;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Semaphore returns a JSON ARRAY of these (one per recipient number sent
 * to), even for a single-recipient send -- e.g.:
 * [{"message_id": 123456, "status": "Pending", "recipient": "639171234567", ...}]
 * We deliberately model only the fields we consume.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SemaphoreMessageResponse(
        @JsonProperty("message_id") Long messageId,
        @JsonProperty("status") String status,
        @JsonProperty("recipient") String recipient,
        @JsonProperty("message") String message
) {
}
