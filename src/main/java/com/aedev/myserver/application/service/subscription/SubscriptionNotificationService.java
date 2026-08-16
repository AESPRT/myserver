package com.aedev.myserver.application.service.subscription;

import com.aedev.myserver.application.exception.SemaphoreIntegrationException;
import com.aedev.myserver.application.service.sms.SemaphoreService;
import com.aedev.myserver.domain.entity.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sends subscription-related SMS notifications via Semaphore. Kept
 * separate from WebhookProcessingService/SubscriptionRecordService
 * deliberately: SMS delivery is a best-effort side-effect, not part of
 * the transactional core. If Semaphore is down, the subscription must
 * still be correctly marked PAST_DUE in the DB -- we should not roll
 * back a real payment-failure record just because a notification
 * couldn't be sent. Every method here swallows SemaphoreIntegrationException
 * after logging it, rather than letting it propagate into the caller's
 * @Transactional method and trigger a rollback.
 */
@Service
public class SubscriptionNotificationService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionNotificationService.class);

    private final SemaphoreService semaphoreService;

    public SubscriptionNotificationService(SemaphoreService semaphoreService) {
        this.semaphoreService = semaphoreService;
    }

    /**
     * Notifies the user their subscription payment failed and the
     * subscription is now past due. Requires a phone number to be
     * present on the Subscription (or wherever your schema stores it --
     * see note below); silently no-ops if there isn't one, since not
     * every user necessarily has SMS opted in.
     */
    public void notifyPastDue(Subscription subscription, String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            log.info("No phone number on file for user {}; skipping past-due SMS", subscription.getUserId());
            return;
        }

        String message = "Your subscription payment failed and your account is now past due. "
                + "Please update your payment method to avoid service interruption.";

        try {
            semaphoreService.send(phoneNumber, message);
        } catch (SemaphoreIntegrationException e) {
            log.error("Failed to send past-due SMS to user {} (phone={}): {}",
                    subscription.getUserId(), phoneNumber, e.getMessage());
        }
    }
}
