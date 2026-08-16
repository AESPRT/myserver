package com.aedev.myserver.domain.enums;

/**
 * Billing cadence for a rental subscription.
 * ANNUAL receives a 15% discount versus paying MONTHLY x12 -- see
 * RentalPlan#priceFor(BillingCycle) for the calculation.
 */
public enum BillingCycle {
    MONTHLY,
    ANNUAL
}
