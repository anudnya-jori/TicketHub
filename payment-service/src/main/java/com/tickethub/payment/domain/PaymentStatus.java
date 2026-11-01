package com.tickethub.payment.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * INITIATED -> SUCCESS | FAILED | PENDING (provider timed out); PENDING -> SUCCESS | FAILED (webhook).
 * Phase 2: SUCCESS -> REFUNDED (compensation when Booking rejects or cancels, Scenario 5). Nothing else ever moves backwards.
 */
public enum PaymentStatus {
    INITIATED, PENDING, SUCCESS, FAILED, REFUNDED;

    public Set<PaymentStatus> allowedNext() {
        return switch (this) {
            case INITIATED -> EnumSet.of(SUCCESS, FAILED, PENDING);
            case PENDING -> EnumSet.of(SUCCESS, FAILED);
            case SUCCESS -> EnumSet.of(REFUNDED);
            case FAILED, REFUNDED -> EnumSet.noneOf(PaymentStatus.class);
        };
    }

    public boolean canTransitionTo(PaymentStatus next) {
        return allowedNext().contains(next);
    }

    /** The provider's outcome is known: a webhook can no longer change this payment. */
    public boolean isFinal() {
        return this == SUCCESS || this == FAILED || this == REFUNDED;
    }
}
