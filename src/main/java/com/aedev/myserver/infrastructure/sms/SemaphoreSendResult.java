package com.aedev.myserver.infrastructure.sms;

/**
 * What SemaphoreService hands back to callers -- deliberately not the
 * raw SemaphoreMessageResponse, so business services (e.g.,
 * WebhookProcessingService) depend on a small stable contract instead of
 * Semaphore's exact API response shape.
 */
public record SemaphoreSendResult(
        Long messageId,
        String status,
        String recipient
) {
}
