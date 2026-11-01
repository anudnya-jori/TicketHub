package com.tickethub.payment.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

/** Payment's own copy of the Booking -> Payment contract (BookingRejected, BookingCancelled). Unknown fields ignored. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BookingEventMessage(String eventId, String eventType, Integer version, Instant occurredAt, Long bookingId,
                                  Long aggregateVersion, String customerId, BigDecimal amount, String currency,
                                  String reason) {

    public static final String REJECTED = "BookingRejected";
    public static final String CANCELLED = "BookingCancelled";

    public long aggregateVersionOrZero() {
        return aggregateVersion == null ? 0L : aggregateVersion;
    }
}
