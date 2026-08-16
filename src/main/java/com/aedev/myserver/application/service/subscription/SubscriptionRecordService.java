package com.aedev.myserver.application.service.subscription;

import com.aedev.myserver.domain.entity.Subscription;
import com.aedev.myserver.domain.enums.BillingCycle;
import com.aedev.myserver.domain.enums.SubscriptionStatus;
import com.aedev.myserver.domain.repository.SubscriptionRepository;
import com.aedev.myserver.infrastructure.security.ApiKeyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class SubscriptionRecordService {

    private final SubscriptionRepository subscriptionRepository;
    private final ApiKeyService apiKeyService;

    public SubscriptionRecordService(
            SubscriptionRepository subscriptionRepository,
            ApiKeyService apiKeyService
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.apiKeyService = apiKeyService;
    }

    /**
     * Creates or reactivates a subscription after the initial
     * checkout_session.payment.paid event.
     * <p>
     * A new API key is generated and associated with the subscription.
     * Only the hash is persisted in the database.
     * <p>
     * The plaintext API key is returned through ApiKeyResult so that
     * the application can send it to the customer once.
     */
    @Transactional
    public ApiKeyService.ApiKeyResult activateNewSubscription(
            String userId,
            String packageId,
            BillingCycle billingCycle
    ) {
        Subscription subscription = subscriptionRepository
                .findByUserId(userId)
                .orElseGet(() -> Subscription.builder()
                        .userId(userId)
                        .build());

        subscription.setPackageId(packageId);
        subscription.setBillingCycle(billingCycle);
        subscription.setStatus(SubscriptionStatus.ACTIVE);

        OffsetDateTime expiresAt = nextExpiration(
                null,
                billingCycle
        );

        subscription.setExpiresAt(expiresAt);

        /*
         * Generate a completely new API key for the subscription.
         */
        ApiKeyService.ApiKeyResult apiKey = apiKeyService.generate();

        subscription.setApiKeyId(apiKey.keyId());
        subscription.setApiKeyHash(apiKey.hash());

        subscriptionRepository.save(subscription);

        return apiKey;
    }

    /**
     * Renews an existing subscription after a successful recurring payment.
     * <p>
     * A new API key is generated and replaces the previous API key.
     * The new key uses the new subscription expiration date.
     */
    @Transactional
    public ApiKeyService.ApiKeyResult renewSubscription(
            Subscription subscription
    ) {
        OffsetDateTime expiresAt = nextExpiration(
                subscription.getExpiresAt(),
                subscription.getBillingCycle()
        );

        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setExpiresAt(expiresAt);

        /*
         * Rotate the API key on renewal.
         */
        ApiKeyService.ApiKeyResult apiKey = apiKeyService.generate();

        subscription.setApiKeyId(apiKey.keyId());
        subscription.setApiKeyHash(apiKey.hash());

        subscriptionRepository.save(subscription);

        return apiKey;
    }

    /**
     * Marks an already-locked subscription as PAST_DUE.
     * <p>
     * The API key is not immediately deleted because the subscription
     * expiration date remains the source of truth for access expiration.
     */
    @Transactional
    public void markPastDue(Subscription subscription) {
        subscription.setStatus(SubscriptionStatus.PAST_DUE);

        subscriptionRepository.save(subscription);
    }

    private OffsetDateTime nextExpiration(
            OffsetDateTime currentExpiresAt,
            BillingCycle cycle
    ) {
        OffsetDateTime now = OffsetDateTime.now();

        OffsetDateTime base =
                currentExpiresAt != null && currentExpiresAt.isAfter(now)
                        ? currentExpiresAt
                        : now;

        return cycle == BillingCycle.ANNUAL
                ? base.plusMonths(12)
                : base.plusMonths(1);
    }
}