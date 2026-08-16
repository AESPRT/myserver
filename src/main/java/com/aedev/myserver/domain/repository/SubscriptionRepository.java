package com.aedev.myserver.domain.repository;

import com.aedev.myserver.domain.entity.Subscription;
import com.aedev.myserver.domain.enums.BillingCycle;
import com.aedev.myserver.domain.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByUserId(String userId);

    /**
     * Locks the subscription row for update within the current transaction.
     * Used by webhook processing (Step 16) when reading-then-extending
     * expiresAt, to prevent a race condition if two webhook deliveries for
     * the same user are processed concurrently (e.g., duplicate delivery
     * arriving at a second thread before the first transaction commits).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Subscription s WHERE s.userId = :userId")
    Optional<Subscription> findByUserIdForUpdate(String userId);

    Optional<Subscription> findByUserIdAndPackageIdAndBillingCycleAndStatus(
            String userId, String packageId, BillingCycle billingCycle, SubscriptionStatus status);

    /**
     * Looks up the subscription owning a given API key ID. Used by
     * ApiKeyAuthenticationFilter to authenticate incoming requests --
     * only the keyId (not the secret) is used for the lookup; the
     * actual secret is verified by comparing hashes after this returns.
     */
    Optional<Subscription> findByApiKeyId(String apiKeyId);
}