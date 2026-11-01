package com.tickethub.booking.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Booking's own copy of the Payment -> Booking contract (PaymentSucceeded, PaymentFailed, PaymentRefunded).
 * Unknown fields are ignored so Payment can add fields without breaking Booking.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentEventMessage(String eventId, String eventType, Integer version, Instant occurredAt, Long bookingId,
                                  Long aggregateVersion, Long paymentId, BigDecimal amount, String currency,
                                  String reason) {

    public static final String SUCCEEDED = "PaymentSucceeded";
    public static final String FAILED = "PaymentFailed";
    public static final String REFUNDED = "PaymentRefunded";

    public long aggregateVersionOrZero() {
        return aggregateVersion == null ? 0L : aggregateVersion;
    }
}
