package com.aedev.myserver.domain.enums;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Rental subscription plan catalog. Mirrors the original Node.js
 * RENTAL_PLANS_BACKEND constant.
 * <p>
 * packageId is the external-facing identifier used in API requests
 * (checkout/change-plan) and in PayMongo metadata -- it is intentionally
 * decoupled from the Java enum constant name so the API contract doesn't
 * break if we ever need to rename the enum constant internally.
 */
@Getter
public enum Plan {

    FREE("free", "Free", BigDecimal.ZERO),
    STARTER("starter", "Starter", new BigDecimal("499.00")),
    PRO("pro", "Pro", new BigDecimal("999.00")),
    BUSINESS("business", "Business", new BigDecimal("2499.00"));

    private static final BigDecimal ANNUAL_MULTIPLIER = new BigDecimal("12");
    private static final BigDecimal ANNUAL_DISCOUNT_FACTOR = new BigDecimal("0.85");

    private final String packageId;
    private final String displayName;
    private final BigDecimal monthlyPrice;

    Plan(String packageId, String displayName, BigDecimal monthlyPrice) {
        this.packageId = packageId;
        this.displayName = displayName;
        this.monthlyPrice = monthlyPrice;
    }

    public boolean isFree() {
        return this == FREE;
    }

    /**
     * Computes the checkout price for this plan under the given billing
     * cycle. ANNUAL applies the 15% discount to (monthlyPrice x 12),
     * matching the original: monthlyPrice * 12 * 0.85
     */
    public BigDecimal priceFor(BillingCycle cycle) {
        if (cycle == BillingCycle.MONTHLY) {
            return monthlyPrice.setScale(2, RoundingMode.HALF_UP);
        }
        return monthlyPrice
                .multiply(ANNUAL_MULTIPLIER)
                .multiply(ANNUAL_DISCOUNT_FACTOR)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Resolves a plan by its external packageId.
     * Throws IllegalArgumentException if no matching plan exists --
     * callers (services) are responsible for translating this into a
     * proper 400 response via the exception handler (Step 19).
     */
    public static Plan fromPackageId(String packageId) {
        for (Plan plan : values()) {
            if (plan.packageId.equalsIgnoreCase(packageId)) {
                return plan;
            }
        }
        throw new IllegalArgumentException("Unknown packageId: " + packageId);
    }
}