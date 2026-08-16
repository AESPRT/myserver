package com.aedev.myserver.presentation.controller.subscription;

import com.aedev.myserver.application.dto.subscription.PayMongoWebhookPayload;
import com.aedev.myserver.application.exception.DuplicateWebhookEventException;
import com.aedev.myserver.application.exception.InvalidWebhookPayloadException;
import com.aedev.myserver.application.exception.WebhookSignatureException;
import com.aedev.myserver.application.service.subscription.WebhookProcessingService;
import com.aedev.myserver.infrastructure.payment.paymongo.PayMongoSignatureVerifier;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/subscription")
public class PayMongoWebhookController {

    private static final Logger log =
            LoggerFactory.getLogger(PayMongoWebhookController.class);

    private static final String SIGNATURE_HEADER = "Paymongo-Signature";

    private final WebhookProcessingService webhookProcessingService;
    private final PayMongoSignatureVerifier signatureVerifier;
    private final ObjectMapper objectMapper;

    @Value("${paymongo.webhook-secret}")
    private String webhookSecret;

    @Value("${paymongo.live-mode:false}")
    private boolean liveMode;

    public PayMongoWebhookController(
            WebhookProcessingService webhookProcessingService,
            PayMongoSignatureVerifier signatureVerifier,
            ObjectMapper objectMapper
    ) {
        this.webhookProcessingService = webhookProcessingService;
        this.signatureVerifier = signatureVerifier;
        this.objectMapper = objectMapper;
    }

    @PostMapping(
            value = "/paymongo-webhook",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = SIGNATURE_HEADER, required = false)
            String signatureHeader
    ) {

        log.info("Received PayMongo webhook");

        // --------------------------------------------------
        // 1. Verify signature
        // --------------------------------------------------

        verifySignature(rawBody, signatureHeader);

        // --------------------------------------------------
        // 2. Parse payload
        // --------------------------------------------------

        PayMongoWebhookPayload payload = parsePayload(rawBody);

        // --------------------------------------------------
        // 3. Log basic information
        // --------------------------------------------------

        log.info(
                "PayMongo webhook event received: eventId={}, type={}",
                payload.data() != null ? payload.data().eventId() : null,
                payload.data() != null && payload.data().attributes() != null
                        ? payload.data().attributes().type()
                        : null
        );

        // --------------------------------------------------
        // 4. Process
        // --------------------------------------------------

        try {

            webhookProcessingService.process(payload);

            return ResponseEntity.ok(
                    Map.of(
                            "success", true,
                            "message", "Webhook processed"
                    )
            );

        } catch (DuplicateWebhookEventException e) {

            log.info(
                    "Duplicate PayMongo webhook acknowledged: {}",
                    e.getMessage()
            );

            return ResponseEntity.ok(
                    Map.of(
                            "success", true,
                            "message", "Webhook already processed"
                    )
            );
        }
    }

    private void verifySignature(
            String rawBody,
            String signatureHeader
    ) {

        if (signatureHeader == null || signatureHeader.isBlank()) {

            log.warn(
                    "PayMongo webhook rejected: missing signature header"
            );

            throw new WebhookSignatureException();
        }

        boolean valid = signatureVerifier.verify(
                rawBody,
                signatureHeader,
                webhookSecret,
                liveMode
        );

        if (!valid) {

            log.warn(
                    "PayMongo webhook rejected: invalid signature"
            );

            throw new WebhookSignatureException();
        }

        log.debug("PayMongo webhook signature verified successfully");
    }

    private PayMongoWebhookPayload parsePayload(String rawBody) {

        try {

            return objectMapper.readValue(
                    rawBody,
                    PayMongoWebhookPayload.class
            );

        } catch (JsonProcessingException e) {

            log.warn(
                    "PayMongo webhook rejected: malformed JSON: {}",
                    e.getMessage()
            );

            throw new InvalidWebhookPayloadException(
                    "Malformed webhook JSON"
            );

        } catch (Exception e) {

            log.error(
                    "PayMongo webhook parse failure",
                    e
            );

            throw new InvalidWebhookPayloadException(
                    "Unable to parse webhook payload"
            );
        }
    }
}