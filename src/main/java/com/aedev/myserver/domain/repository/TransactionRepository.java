package com.aedev.myserver.domain.repository;

import com.aedev.myserver.domain.entity.Transaction;
import com.aedev.myserver.domain.enums.BillingCycle;
import com.aedev.myserver.domain.enums.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByReferenceNumber(String referenceNumber);

    Optional<Transaction> findByPaymongoEventId(String paymongoEventId);

    boolean existsByPaymongoEventId(String paymongoEventId);

    Optional<Transaction> findByCheckoutSessionId(String checkoutSessionId);

    Optional<Transaction> findFirstByUserIdAndPackageIdAndBillingCycleAndStatusOrderByCreatedAtDesc(
            String userId, String packageId, BillingCycle billingCycle, TransactionStatus status);
}