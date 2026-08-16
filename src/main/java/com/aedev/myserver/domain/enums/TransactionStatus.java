package com.aedev.myserver.domain.enums;

/**
 * Status of a single FoldAndGoTransaction record.
 *
 * PENDING - checkout session created, payment not yet confirmed
 * PAID    - payment confirmed via webhook
 * FAILED  - payment failed per webhook
 */
public enum TransactionStatus {
    PENDING,
    PAID,
    FAILED
}