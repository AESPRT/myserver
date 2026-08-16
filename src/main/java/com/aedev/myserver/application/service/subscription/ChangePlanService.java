package com.aedev.myserver.application.service.subscription;

import com.aedev.myserver.application.dto.subscription.ChangePlanRequest;
import com.aedev.myserver.application.dto.subscription.CheckoutMetadata;
import com.aedev.myserver.application.dto.subscription.CheckoutResponse;
import com.aedev.myserver.application.exception.DuplicateCheckoutException;
import com.aedev.myserver.application.exception.FreePlanCheckoutException;
import com.aedev.myserver.application.exception.InvalidPlanException;
import com.aedev.myserver.application.exception.NoExistingSubscriptionException;
import com.aedev.myserver.application.exception.SamePlanChangeException;
import com.aedev.myserver.application.util.ReferenceNumberGenerator;
import com.aedev.myserver.domain.entity.Subscription;
import com.aedev.myserver.domain.entity.Transaction;
import com.aedev.myserver.domain.enums.Plan;
import com.aedev.myserver.domain.enums.TransactionStatus;
import com.aedev.myserver.domain.repository.SubscriptionRepository;
import com.aedev.myserver.domain.repository.TransactionRepository;
import com.aedev.myserver.infrastructure.payment.paymongo.PayMongoCheckoutResult;
import com.aedev.myserver.infrastructure.payment.paymongo.PayMongoService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class ChangePlanService {

    private static final long PENDING_CHECKOUT_WINDOW_MINUTES = 15;

    private final PayMongoService payMongoService;
    private final TransactionRecordService transactionRecordService;
    private final ReferenceNumberGenerator referenceNumberGenerator;
    private final SubscriptionRepository subscriptionRepository;
    private final TransactionRepository transactionRepository;

    public ChangePlanService(
            PayMongoService payMongoService,
            TransactionRecordService transactionRecordService,
            ReferenceNumberGenerator referenceNumberGenerator,
            SubscriptionRepository subscriptionRepository,
            TransactionRepository transactionRepository
    ) {
        this.payMongoService = payMongoService;
        this.transactionRecordService = transactionRecordService;
        this.referenceNumberGenerator = referenceNumberGenerator;
        this.subscriptionRepository = subscriptionRepository;
        this.transactionRepository = transactionRepository;
    }

    public CheckoutResponse changePlan(ChangePlanRequest request) {
        Plan newPlan = resolvePlan(request.planId());

        if (newPlan.isFree()) {
            throw new FreePlanCheckoutException();
        }

        Subscription existing = subscriptionRepository.findByUserId(request.userId())
                .orElseThrow(NoExistingSubscriptionException::new);

        if (existing.getPackageId().equals(newPlan.getPackageId())
                && existing.getBillingCycle() == request.cycle()) {
            throw new SamePlanChangeException();
        }

        rejectIfChangeInProgress(request, newPlan);

        BigDecimal amount = newPlan.priceFor(request.cycle());
        String referenceNumber = referenceNumberGenerator.generate();

        CheckoutMetadata metadata = CheckoutMetadata.of(
                request.userId(),
                newPlan.getPackageId(),
                request.cycle(),
                request.cusEmail(),
                request.cusName(),
                request.cusPhone(),
                referenceNumber
        );

        Transaction pending = transactionRecordService.savePending(
                referenceNumber, request.userId(), newPlan, request.cycle(), amount
        );

        PayMongoCheckoutResult result = payMongoService.createCheckoutSession(
                true,
                newPlan.getDisplayName(),
                amount,
                metadata
        );

        transactionRecordService.attachCheckoutSession(pending.getId(), result.checkoutSessionId());

        return new CheckoutResponse(
                result.checkoutUrl(),
                referenceNumber,
                amount,
                metadata
        );
    }

    private void rejectIfChangeInProgress(ChangePlanRequest request, Plan newPlan) {
        Optional<Transaction> recentPending = transactionRepository
                .findFirstByUserIdAndPackageIdAndBillingCycleAndStatusOrderByCreatedAtDesc(
                        request.userId(), newPlan.getPackageId(), request.cycle(), TransactionStatus.PENDING
                );

        recentPending.ifPresent(transaction -> {
            OffsetDateTime windowStart = OffsetDateTime.now().minusMinutes(PENDING_CHECKOUT_WINDOW_MINUTES);
            if (transaction.getCreatedAt().isAfter(windowStart)) {
                throw new DuplicateCheckoutException(transaction.getReferenceNumber());
            }
        });
    }

    private Plan resolvePlan(String planId) {
        try {
            return Plan.fromPackageId(planId);
        } catch (IllegalArgumentException e) {
            throw new InvalidPlanException(planId);
        }
    }
}