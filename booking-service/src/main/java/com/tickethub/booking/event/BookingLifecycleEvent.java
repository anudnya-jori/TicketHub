package com.tickethub.booking.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Booking -> Payment (Scenario 5): the compensation triggers. Topic booking.events, key = bookingId.
 *   BookingRejected   the booking cannot be confirmed (payment arrived after expiry/cancellation) -> refund
 *   BookingCancelled  the customer cancelled a CONFIRMED booking -> refund
 * Same envelope as every Phase 2 event: eventId, eventType, version, occurredAt, bookingId, aggregateVersion.
 */
public record BookingLifecycleEvent(String eventId, String eventType, int version, Instant occurredAt, Long bookingId,
                                    long aggregateVersion, String customerId, BigDecimal amount, String currency,
                                    String reason) {

    public static final String REJECTED = "BookingRejected";
    public static final String CANCELLED = "BookingCancelled";

    public static String idFor(String eventType, Long bookingId, long aggregateVersion) {
        return (REJECTED.equals(eventType) ? "booking-rejected-" : "booking-cancelled-") + bookingId + "-v" + aggregateVersion;
    }
}
