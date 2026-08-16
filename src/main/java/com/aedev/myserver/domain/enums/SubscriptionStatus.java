package com.aedev.myserver.domain.enums;

/**
 * Lifecycle status of a RentalSubscription.
 *
 * ACTIVE    - payment succeeded, subscription usable, within expiry window
 * PAST_DUE  - a renewal/initial payment failed; subscription is not usable
 *             until a subsequent successful payment moves it back to ACTIVE
 * CANCELLED - reserved for future use (not in original Node.js scope, but
 *             modeling it now avoids a painful migration later if/when
 *             user-initiated cancellation is added)
 */
public enum SubscriptionStatus {
    ACTIVE,
    PAST_DUE,
    CANCELLED
}
