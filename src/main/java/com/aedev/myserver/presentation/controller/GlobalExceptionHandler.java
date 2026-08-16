package com.aedev.myserver.presentation.controller;

import com.aedev.myserver.application.exception.*;
import com.aedev.myserver.infrastructure.tts.ElevenLabsIntegrationException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ------------------------------------------------------------------
    // Checkout / change-plan domain exceptions
    // ------------------------------------------------------------------

    @ExceptionHandler(InvalidPlanException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidPlan(
            InvalidPlanException e
    ) {
        return respond(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(FreePlanCheckoutException.class)
    public ResponseEntity<Map<String, Object>> handleFreePlanCheckout(
            FreePlanCheckoutException e
    ) {
        return respond(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(AlreadySubscribedException.class)
    public ResponseEntity<Map<String, Object>> handleAlreadySubscribed(
            AlreadySubscribedException e
    ) {
        return respond(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(DuplicateCheckoutException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateCheckout(
            DuplicateCheckoutException e
    ) {
        return respond(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(NoExistingSubscriptionException.class)
    public ResponseEntity<Map<String, Object>> handleNoExistingSubscription(
            NoExistingSubscriptionException e
    ) {
        return respond(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(SamePlanChangeException.class)
    public ResponseEntity<Map<String, Object>> handleSamePlanChange(
            SamePlanChangeException e
    ) {
        return respond(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    // ------------------------------------------------------------------
    // Webhook exceptions
    // ------------------------------------------------------------------

    @ExceptionHandler(WebhookSignatureException.class)
    public ResponseEntity<Map<String, Object>> handleWebhookSignature(
            WebhookSignatureException e
    ) {
        log.warn("Webhook signature rejected: {}", e.getMessage());

        return respond(
                HttpStatus.UNAUTHORIZED,
                e.getMessage()
        );
    }

    @ExceptionHandler(InvalidWebhookPayloadException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidWebhookPayload(
            InvalidWebhookPayloadException e
    ) {
        log.warn("Webhook payload rejected: {}", e.getMessage());

        return respond(
                HttpStatus.BAD_REQUEST,
                e.getMessage()
        );
    }

    /**
     * Handles the race where two webhook deliveries of the same
     * event pass the existsByEventId() check concurrently, and
     * the database unique constraint rejects the second insert.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(
            DataIntegrityViolationException e
    ) {
        log.info(
                "Data integrity violation, likely a concurrent duplicate webhook delivery: {}",
                e.getMessage()
        );

        return respond(
                HttpStatus.OK,
                "duplicate event acknowledged"
        );
    }

    @ExceptionHandler(DuplicateWebhookEventException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateWebhookEvent(
            DuplicateWebhookEventException e
    ) {
        log.info(e.getMessage());

        return respond(
                HttpStatus.OK,
                "duplicate event acknowledged"
        );
    }

    // ------------------------------------------------------------------
    // Email
    // ------------------------------------------------------------------

    @ExceptionHandler(EmailServiceException.class)
    public ResponseEntity<Map<String, Object>> handleEmailServiceException(
            EmailServiceException e
    ) {
        log.error("Email service error: {}", e.getMessage(), e);

        return respond(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unable to send email. Please try again later."
        );
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException e
    ) {
        String message = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("Validation failed");

        return respond(
                HttpStatus.BAD_REQUEST,
                message
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(
            ConstraintViolationException e
    ) {
        return respond(
                HttpStatus.BAD_REQUEST,
                e.getMessage()
        );
    }

    // ------------------------------------------------------------------
    // Downstream / infrastructure failures
    // ------------------------------------------------------------------

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<Map<String, Object>> handlePayMongoApiFailure(
            RestClientException e
    ) {
        log.error("PayMongo API call failed", e);

        return respond(
                HttpStatus.BAD_GATEWAY,
                "Payment provider is temporarily unavailable. Please try again."
        );
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDatabaseFailure(
            DataAccessException e
    ) {
        log.error("Database operation failed", e);

        return respond(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "A database error occurred. Please try again."
        );
    }

    // ------------------------------------------------------------------
    // RAG
    // ------------------------------------------------------------------

    @ExceptionHandler(RagCollectionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleRagCollectionNotFound(
            RagCollectionNotFoundException e
    ) {
        log.warn("RAG collection not found: {}", e.getMessage());

        return respond(
                HttpStatus.NOT_FOUND,
                e.getMessage()
        );
    }

    @ExceptionHandler(CohereIntegrationException.class)
    public ResponseEntity<Map<String, Object>> handleCohereIntegrationFailure(
            CohereIntegrationException e
    ) {
        log.error("Cohere API failure: {}", e.getMessage(), e);

        return respond(
                HttpStatus.BAD_GATEWAY,
                "AI service is temporarily unavailable. Please try again later."
        );
    }

    // ------------------------------------------------------------------
    // Fallback
    // ------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(
            Exception e
    ) {
        log.error("Unhandled exception", e);

        return respond(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred."
        );
    }

    // ------------------------------------------------------------------
    // Shared response shape
    // ------------------------------------------------------------------

    private ResponseEntity<Map<String, Object>> respond(
            HttpStatus status,
            String message
    ) {
        Map<String, Object> body = new LinkedHashMap<>();

        body.put(
                "timestamp",
                OffsetDateTime.now().toString()
        );

        body.put(
                "status",
                status.value()
        );

        body.put(
                "error",
                status.getReasonPhrase()
        );

        body.put(
                "message",
                message
        );

        return ResponseEntity
                .status(status)
                .body(body);
    }

    // ------------------------------------------------------------------
    // TTS / audio
    // ------------------------------------------------------------------

    @ExceptionHandler(ElevenLabsIntegrationException.class)
    public ResponseEntity<Map<String, Object>> handleElevenLabsFailure(
            ElevenLabsIntegrationException e
    ) {
        log.error("ElevenLabs API failure: {}", e.getMessage());

        if (e.getUpstreamStatus() != null && e.getUpstreamStatus().value() == 429) {
            return respond(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Text-to-speech service rate limit reached. Please try again shortly."
            );
        }

        if (e.getUpstreamStatus() != null && e.getUpstreamStatus().value() == 402) {
            return respond(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Text-to-speech service is temporarily unavailable. Please try again later."
            );
        }

        return respond(
                HttpStatus.BAD_GATEWAY,
                "Text-to-speech service is temporarily unavailable. Please try again later."
        );
    }

    @ExceptionHandler(AudioFileMissingException.class)
    public ResponseEntity<Map<String, Object>> handleAudioFileMissing(
            AudioFileMissingException e
    ) {
        log.error("Audio file missing on disk: {}", e.getMessage());

        return respond(
                HttpStatus.NOT_FOUND,
                e.getMessage()
        );
    }

    @ExceptionHandler(SemaphoreIntegrationException.class)
    public ResponseEntity<Map<String, Object>> handleSemaphoreFailure(SemaphoreIntegrationException e) {
        log.error("Semaphore SMS send failed", e);
        return respond(HttpStatus.BAD_GATEWAY, "SMS provider is temporarily unavailable. Please try again.");
    }
}