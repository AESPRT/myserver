package com.aedev.myserver.application.service.subscription;

import com.aedev.myserver.application.dto.subscription.PayMongoWebhookPayload;
import com.aedev.myserver.application.exception.DuplicateWebhookEventException;
import com.aedev.myserver.application.exception.InvalidWebhookPayloadException;
import com.aedev.myserver.domain.entity.ProcessedWebhookEvent;
import com.aedev.myserver.domain.entity.Subscription;
import com.aedev.myserver.domain.entity.Transaction;
import com.aedev.myserver.domain.enums.BillingCycle;
import com.aedev.myserver.domain.enums.Plan;
import com.aedev.myserver.domain.enums.TransactionStatus;
import com.aedev.myserver.domain.repository.ProcessedWebhookEventRepository;
import com.aedev.myserver.domain.repository.SubscriptionRepository;
import com.aedev.myserver.domain.repository.TransactionRepository;
import com.aedev.myserver.infrastructure.security.ApiKeyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class WebhookProcessingService {

    private static final Logger log = LoggerFactory.getLogger(WebhookProcessingService.class);

    private static final Set<String> INITIAL_PAYMENT_EVENTS = Set.of(
            "checkout_session.payment.paid"
    );

    private static final Set<String> RECURRING_PAYMENT_EVENTS = Set.of(
            "payment.paid",
            "invoice.payment_succeeded"
    );

    private static final Set<String> FAILED_PAYMENT_EVENTS = Set.of(
            "payment.failed",
            "invoice.payment_failed",
            "checkout_session.payment.failed"
    );

    private final ProcessedWebhookEventRepository processedWebhookEventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionRecordService subscriptionRecordService;
    private final TransactionRepository transactionRepository;

    public WebhookProcessingService(
            ProcessedWebhookEventRepository processedWebhookEventRepository,
            SubscriptionRepository subscriptionRepository,
            SubscriptionRecordService subscriptionRecordService,
            TransactionRepository transactionRepository
    ) {
        this.processedWebhookEventRepository = processedWebhookEventRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionRecordService = subscriptionRecordService;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Flattened, null-checked view of the fields handlers actually need
     * from the deeply nested PayMongo payload
     * (data.attributes.data.attributes.*). Resolved once per webhook via
     * {@link #resolve(PayMongoWebhookPayload)} instead of re-walking the
     * chain in every handler -- each repetition of the raw chain was a
     * separate NPE risk and a separate place to update if PayMongo's
     * shape ever changes.
     */
    private record WebhookEvent(
            String eventId,
            String eventType,
            String resourceId,
            BigDecimal amount,
            Map<String, String> metadata
    ) {
    }

    /**
     * Entry point called by the controller after signature verification.
     * The whole method is one DB transaction: event-dedup insert, transaction
     * record, and subscription update either all commit or all rollback.
     */
    @Transactional
    public void process(PayMongoWebhookPayload payload) {
        WebhookEvent event = resolve(payload);

        // Idempotency guard. Relies on the unique constraint on event_id --
        // if two deliveries of the same event race each other here, the
        // second insert throws a DataIntegrityViolationException which the
        // controller/exception handler treats as a benign duplicate (still
        // returns 200 to PayMongo so it stops retrying).
        if (processedWebhookEventRepository.existsByEventId(event.eventId())) {
            log.info("Skipping already-processed PayMongo webhook event {}", event.eventId());
            throw new DuplicateWebhookEventException(event.eventId());
        }
        processedWebhookEventRepository.save(
                ProcessedWebhookEvent.builder()
                        .eventId(event.eventId())
                        .eventType(event.eventType())
                        .build()
        );

        if (INITIAL_PAYMENT_EVENTS.contains(event.eventType())) {
            handleInitialPayment(event);
        } else if (RECURRING_PAYMENT_EVENTS.contains(event.eventType())) {
            handleRecurringPayment(event);
        } else if (FAILED_PAYMENT_EVENTS.contains(event.eventType())) {
            handleFailedPayment(event);
        } else {
            log.info("Ignoring unhandled PayMongo webhook event type: {}", event.eventType());
        }
    }

    /**
     * Walks data.attributes.data.attributes.* exactly once and fails fast
     * with a clear message if any link in the chain is missing, instead of
     * letting a stray null surface as an NPE deep inside a handler.
     */
    private WebhookEvent resolve(PayMongoWebhookPayload payload) {

        if (payload == null || payload.data() == null) {
            throw new InvalidWebhookPayloadException("Missing data envelope");
        }

        String eventId = payload.data().eventId();

        if (eventId == null || eventId.isBlank()) {
            throw new InvalidWebhookPayloadException("Missing event id");
        }

        var eventAttributes = payload.data().attributes();

        if (eventAttributes == null || eventAttributes.type() == null) {
            throw new InvalidWebhookPayloadException("Missing event type");
        }

        String eventType = eventAttributes.type();

        var resource = eventAttributes.resourceData();

        if (resource == null || resource.resourceId() == null) {
            throw new InvalidWebhookPayloadException("Missing resource data");
        }

        String resourceId = resource.resourceId();

        var resourceAttributes = resource.attributes();

        if (resourceAttributes == null) {
            throw new InvalidWebhookPayloadException("Missing resource attributes");
        }

        /*
         * ------------------------------------------------------------
         * Metadata
         * ------------------------------------------------------------
         *
         * For checkout_session.payment.paid, metadata belongs to
         * the Checkout Session attributes.
         */
        Map<String, String> metadata = resourceAttributes.metadata();

        BigDecimal amount = getAmount(metadata, resourceAttributes, eventType);

        log.info(
                "Resolved PayMongo webhook: eventId={}, type={}, resourceId={}, amount={}",
                eventId,
                eventType,
                resourceId,
                amount
        );

        return new WebhookEvent(
                eventId,
                eventType,
                resourceId,
                amount,
                metadata
        );
    }

    private static BigDecimal getAmount(Map<String, String> metadata, PayMongoWebhookPayload.ResourceAttributes resourceAttributes, String eventType) {
        if (metadata == null) {
            throw new InvalidWebhookPayloadException("Missing metadata");
        }

        /*
         * ------------------------------------------------------------
         * Amount
         * ------------------------------------------------------------
         *
         * checkout_session.payment.paid:
         *
         * resource.attributes.payments[].attributes.amount
         *
         * Direct payment events may instead have:
         *
         * resource.attributes.amount
         */
        Long amountInCentavos = resourceAttributes.amount();

        if (amountInCentavos == null
                && resourceAttributes.payments() != null
                && !resourceAttributes.payments().isEmpty()) {

            var payment = resourceAttributes.payments().getFirst();

            if (payment != null && payment.attributes() != null) {
                amountInCentavos = payment.attributes().amount();
            }
        }

        if (amountInCentavos == null) {
            throw new InvalidWebhookPayloadException(
                    "Missing payment amount for event type: " + eventType
            );
        }

        return BigDecimal
                .valueOf(amountInCentavos)
                .movePointLeft(2);
    }

    // ------------------------------------------------------------------
    // Initial payment: checkout_session.payment.paid
    // ------------------------------------------------------------------

    private void handleInitialPayment(WebhookEvent event) {
        String userId = requireMetadata(event.metadata(), "user_id");
        String packageId = requireMetadata(event.metadata(), "package_id");
        BillingCycle billingCycle = BillingCycle.valueOf(requireMetadata(event.metadata(), "billing_cycle"));
        Plan plan = Plan.fromPackageId(packageId);

        markTransactionPaid(event.resourceId());

        ApiKeyService.ApiKeyResult apiKey = subscriptionRecordService.activateNewSubscription(userId, plan.getPackageId(), billingCycle);

        log.info(
                "Activated subscription for user {} (plan={}, cycle={}, apiKey={})",
                userId,
                plan.getPackageId(),
                billingCycle,
                apiKey
        );
    }

    // ------------------------------------------------------------------
    // Recurring payment: payment.paid / invoice.payment_succeeded
    // ------------------------------------------------------------------

    private void handleRecurringPayment(WebhookEvent event) {
        String userId = requireMetadata(event.metadata(), "user_id");

        markTransactionPaid(event.resourceId());

        // Lock the row for the duration of this transaction so a concurrent
        // webhook delivery for the same user can't interleave the read and
        // the extend.
        Subscription subscription = subscriptionRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new InvalidWebhookPayloadException(
                        "Recurring payment received for user with no existing subscription: " + userId));

        ApiKeyService.ApiKeyResult apiKey =
                subscriptionRecordService.renewSubscription(subscription);

        log.info(
                "Renewed subscription for user {} (apiKey={})",
                userId,
                apiKey
        );
    }

    // ------------------------------------------------------------------
    // Failed payment: payment.failed / invoice.payment_failed / checkout_session.payment.failed
    // ------------------------------------------------------------------

    private void handleFailedPayment(WebhookEvent event) {
        markTransactionFailed(event.resourceId());

        String userId = event.metadata().get("user_id");
        if (userId == null) {
            log.warn("Failed payment event {} had no user_id in metadata; subscription not updated",
                    event.eventId());
            return;
        }

        subscriptionRepository.findByUserIdForUpdate(userId).ifPresentOrElse(
                subscriptionRecordService::markPastDue,
                () -> log.warn("Failed payment event {} referenced unknown user {}",
                        event.eventId(), userId)
        );
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    private void markTransactionPaid(String checkoutSessionOrPaymentId) {
        updateTransactionStatus(checkoutSessionOrPaymentId, TransactionStatus.PAID);
    }

    private void markTransactionFailed(String checkoutSessionOrPaymentId) {
        updateTransactionStatus(checkoutSessionOrPaymentId, TransactionStatus.FAILED);
    }

    private void updateTransactionStatus(String checkoutSessionId, TransactionStatus status) {
        Optional<Transaction> transaction = transactionRepository.findByCheckoutSessionId(checkoutSessionId);
        transaction.ifPresentOrElse(
                t -> {
                    t.setStatus(status);
                    transactionRepository.save(t);
                },
                () -> log.warn("No matching transaction found for checkout/payment id {} (status={})",
                        checkoutSessionId, status)
        );
    }

    private String requireMetadata(Map<String, String> metadata, String key) {
        String value = metadata.get(key);
        if (value == null || value.isBlank()) {
            throw new InvalidWebhookPayloadException("Missing required metadata field: " + key);
        }
        return value;
    }
}