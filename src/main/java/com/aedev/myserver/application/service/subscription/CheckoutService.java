package com.aedev.myserver.application.service.subscription;

import com.aedev.myserver.application.dto.subscription.CheckoutMetadata;
import com.aedev.myserver.application.dto.subscription.CheckoutRequest;
import com.aedev.myserver.application.dto.subscription.CheckoutResponse;
import com.aedev.myserver.application.exception.AlreadySubscribedException;
import com.aedev.myserver.application.exception.DuplicateCheckoutException;
import com.aedev.myserver.application.exception.FreePlanCheckoutException;
import com.aedev.myserver.application.exception.InvalidPlanException;
import com.aedev.myserver.application.util.ReferenceNumberGenerator;
import com.aedev.myserver.domain.entity.Transaction;
import com.aedev.myserver.domain.enums.Plan;
import com.aedev.myserver.domain.enums.SubscriptionStatus;
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
public class CheckoutService {

    // Window during which an unresolved PENDING transaction blocks a
    // repeat checkout for the same user/plan/cycle. Chosen to roughly
    // match a typical hosted-checkout session lifetime -- long enough to
    // block accidental double-submits, short enough that a genuinely
    // abandoned attempt doesn't lock the user out indefinitely.
    private static final long PENDING_CHECKOUT_WINDOW_MINUTES = 15;

    private final PayMongoService payMongoService;
    private final TransactionRecordService transactionRecordService;
    private final ReferenceNumberGenerator referenceNumberGenerator;
    private final SubscriptionRepository subscriptionRepository;
    private final TransactionRepository transactionRepository;

    public CheckoutService(
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

    public CheckoutResponse checkout(CheckoutRequest request) {
        Plan plan = resolvePlan(request.packageId());

        if (plan.isFree()) {
            throw new FreePlanCheckoutException();
        }

        rejectIfAlreadySubscribed(request, plan);
        rejectIfCheckoutInProgress(request, plan);

        BigDecimal amount = plan.priceFor(request.cycle());
        String referenceNumber = referenceNumberGenerator.generate();

        CheckoutMetadata metadata = CheckoutMetadata.of(
                request.userId(),
                plan.getPackageId(),
                request.cycle(),
                request.cusEmail(),
                request.cusName(),
                request.cusPhone(),
                referenceNumber
        );

        Transaction pending = transactionRecordService.savePending(
                referenceNumber, request, plan, amount
        );

        PayMongoCheckoutResult result = payMongoService.createCheckoutSession(
                false,
                plan.getDisplayName(),
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

    private void rejectIfAlreadySubscribed(CheckoutRequest request, Plan plan) {
        subscriptionRepository.findByUserIdAndPackageIdAndBillingCycleAndStatus(
                request.userId(), plan.getPackageId(), request.cycle(), SubscriptionStatus.ACTIVE
        ).ifPresent(subscription -> {
            throw new AlreadySubscribedException();
        });
    }

    private void rejectIfCheckoutInProgress(CheckoutRequest request, Plan plan) {
        Optional<Transaction> recentPending = transactionRepository
                .findFirstByUserIdAndPackageIdAndBillingCycleAndStatusOrderByCreatedAtDesc(
                        request.userId(), plan.getPackageId(), request.cycle(), TransactionStatus.PENDING
                );

        recentPending.ifPresent(transaction -> {
            OffsetDateTime windowStart = OffsetDateTime.now().minusMinutes(PENDING_CHECKOUT_WINDOW_MINUTES);
            if (transaction.getCreatedAt().isAfter(windowStart)) {
                throw new DuplicateCheckoutException(transaction.getReferenceNumber());
            }
        });
    }

    private Plan resolvePlan(String packageId) {
        try {
            return Plan.fromPackageId(packageId);
        } catch (IllegalArgumentException e) {
            throw new InvalidPlanException(packageId);
        }
    }
}