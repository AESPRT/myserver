package com.aedev.myserver.application.service.subscription;

import com.aedev.myserver.application.dto.subscription.CheckoutRequest;
import com.aedev.myserver.domain.entity.Transaction;
import com.aedev.myserver.domain.enums.BillingCycle;
import com.aedev.myserver.domain.enums.Plan;
import com.aedev.myserver.domain.enums.TransactionStatus;
import com.aedev.myserver.domain.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Owns writings to transactions as small, independently
 * committed operations. Kept as a real separate bean (not a private
 * method on CheckoutService) so @Transactional is applied via Spring's
 * proxy correctly -- same-class method calls bypass the proxy and would
 * silently ignore @Transactional otherwise.
 */
@Service
public class TransactionRecordService {

    private final TransactionRepository transactionRepository;

    public TransactionRecordService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public Transaction savePending(
            String referenceNumber,
            CheckoutRequest request,
            Plan plan,
            BigDecimal amount
    ) {
        Transaction transaction = Transaction.builder()
                .referenceNumber(referenceNumber)
                .userId(request.userId())
                .packageId(plan.getPackageId())
                .billingCycle(request.cycle())
                .amount(amount)
                .status(TransactionStatus.PENDING)
                .build();

        return transactionRepository.save(transaction);
    }

    @Transactional
    public Transaction savePending(
            String referenceNumber,
            String userId,
            Plan plan,
            BillingCycle billingCycle,
            BigDecimal amount
    ) {
        Transaction transaction = Transaction.builder()
                .referenceNumber(referenceNumber)
                .userId(userId)
                .packageId(plan.getPackageId())
                .billingCycle(billingCycle)
                .amount(amount)
                .status(TransactionStatus.PENDING)
                .build();

        return transactionRepository.save(transaction);
    }

    @Transactional
    public void attachCheckoutSession(Long transactionId, String checkoutSessionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Transaction " + transactionId + " disappeared before session attach"));
        transaction.setCheckoutSessionId(checkoutSessionId);
        transactionRepository.save(transaction);
    }
}