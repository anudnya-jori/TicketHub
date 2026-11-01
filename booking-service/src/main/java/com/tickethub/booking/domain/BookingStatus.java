package com.tickethub.booking.domain;

import java.util.Set;

/**
 * PENDING_PAYMENT -> CONFIRMED | PAYMENT_FAILED | CANCELLED | EXPIRED | REJECTED.
 * Phase 2 additions (the saga's compensations):
 *  - CONFIRMED -> CANCELLED: a customer cancels a paid booking; Payment refunds (BookingCancelled event).
 *  - EXPIRED / CANCELLED / PAYMENT_FAILED -> REJECTED: payment arrived too late; Payment refunds (BookingRejected event).
 *  - A booking is never confirmed from any of these: the money goes back instead.
 */
public enum BookingStatus {
    PENDING_PAYMENT, CONFIRMED, PAYMENT_FAILED, CANCELLED, EXPIRED, REJECTED;

    public Set<BookingStatus> allowedNext() {
        return switch (this) {
            case PENDING_PAYMENT -> Set.of(CONFIRMED, PAYMENT_FAILED, CANCELLED, EXPIRED, REJECTED);
            case CONFIRMED -> Set.of(CANCELLED);
            case EXPIRED, CANCELLED, PAYMENT_FAILED -> Set.of(REJECTED);
            case REJECTED -> Set.of();
        };
    }

    public boolean canTransitionTo(BookingStatus next) {
        return allowedNext().contains(next);
    }
}
