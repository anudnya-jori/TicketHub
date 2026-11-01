package com.tickethub.payment.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payment -> Booking. Topic payment.events, key = bookingId, JSON. One envelope for every Phase 2 event:
 *   eventId, eventType, version (schema), occurredAt, bookingId, aggregateVersion (this payment's version).
 *   PaymentSucceeded  Razorpay/provider captured the payment
 *   PaymentFailed     declined or failed (reason says why)
 *   PaymentRefunded   the compensation finished: the money went back
 */
public record PaymentEvent(String eventId, String eventType, int version, Instant occurredAt, Long bookingId,
                           long aggregateVersion, Long paymentId, BigDecimal amount, String currency, String reason) {

    public static final String SUCCEEDED = "PaymentSucceeded";
    public static final String FAILED = "PaymentFailed";
    public static final String REFUNDED = "PaymentRefunded";

    public static String idFor(String eventType, Long paymentId) {
        return switch (eventType) {
            case SUCCEEDED -> "payment-succeeded-" + paymentId;
            case FAILED -> "payment-failed-" + paymentId;
            default -> "payment-refunded-" + paymentId;
        };
    }
}
