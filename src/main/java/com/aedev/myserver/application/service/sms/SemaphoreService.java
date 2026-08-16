package com.aedev.myserver.application.service.sms;

import com.aedev.myserver.application.exception.SemaphoreIntegrationException;
import com.aedev.myserver.infrastructure.config.SemaphoreProperties;
import com.aedev.myserver.infrastructure.sms.SemaphoreMessageResponse;
import com.aedev.myserver.infrastructure.sms.SemaphoreSendResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;

import static com.aedev.myserver.infrastructure.config.SemaphoreClientConfig.SEMAPHORE_WEB_CLIENT;

/**
 * Dedicated Semaphore SMS integration service. This is the only class in
 * the application that talks to the Semaphore Messages API -- business
 * services never call Semaphore directly, same principle as
 * PayMongoService for payments.
 * <p>
 * Deliberately generic (send a message to a number) rather than tied to
 * any one notification type (past-due alert, OTP, etc.). Callers own the
 * message content and reason for sending; this class owns only "how do
 * we actually deliver an SMS via Semaphore."
 */
@Service
public class SemaphoreService {

    private static final Logger log = LoggerFactory.getLogger(SemaphoreService.class);
    private static final String MESSAGES_PATH = "/messages";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final WebClient semaphoreWebClient;
    private final SemaphoreProperties properties;

    public SemaphoreService(
            @Qualifier(SEMAPHORE_WEB_CLIENT) WebClient semaphoreWebClient,
            SemaphoreProperties properties
    ) {
        this.semaphoreWebClient = semaphoreWebClient;
        this.properties = properties;
    }

    /**
     * Sends a single SMS to a single Philippine mobile number.
     *
     * @param recipientNumber E.164 or local format accepted by Semaphore
     *                        (e.g. "639171234567" or "09171234567") --
     *                        Semaphore normalizes this on their end.
     * @param message         Message body. Semaphore bills per 160-char
     *                        segment; long messages are split and billed
     *                        as multiple segments on their side, not ours.
     */
    public SemaphoreSendResult send(String recipientNumber, String message) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("apikey", properties.apiKey());
        form.add("number", recipientNumber);
        form.add("message", message);
        if (properties.senderName() != null && !properties.senderName().isBlank()) {
            form.add("sendername", properties.senderName());
        }

        List<SemaphoreMessageResponse> response;
        try {
            response = semaphoreWebClient.post()
                    .uri(MESSAGES_PATH)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(form)
                    .retrieve()
                    .bodyToFlux(SemaphoreMessageResponse.class)
                    .collectList()
                    .block(REQUEST_TIMEOUT);
        } catch (WebClientResponseException e) {
            log.error("Semaphore API returned {} for recipient {}: {}",
                    e.getStatusCode(), recipientNumber, e.getResponseBodyAsString());
            throw new SemaphoreIntegrationException(
                    "Semaphore returned " + e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("Semaphore API call failed for recipient {}", recipientNumber, e);
            throw new SemaphoreIntegrationException("Request to Semaphore failed", e);
        }

        if (response == null || response.isEmpty()) {
            throw new SemaphoreIntegrationException("Semaphore returned an empty response");
        }

        SemaphoreMessageResponse first = response.getFirst();
        log.info("Sent SMS via Semaphore: messageId={}, status={}, recipient={}",
                first.messageId(), first.status(), first.recipient());

        return new SemaphoreSendResult(first.messageId(), first.status(), first.recipient());
    }
}