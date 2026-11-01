package com.tickethub.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Notification's own copy of the event contract (ST-19). TOLERANT READER (Scenario 13): unknown fields are ignored, and
 * both version 1 (amount) and version 2 (totalAmount, amount kept during the migration) payloads are understood.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BookingConfirmedEvent(String eventId, Long bookingId, String customerId, String recipient,
                                    Long showId, List<Long> seatIds, BigDecimal amount, String currency,
                                    Instant confirmedAt, BigDecimal totalAmount) {

    public BookingConfirmedEvent(String eventId, Long bookingId, String customerId, String recipient, Long showId,
                                 List<Long> seatIds, BigDecimal amount, String currency, Instant confirmedAt) {
        this(eventId, bookingId, customerId, recipient, showId, seatIds, amount, currency, confirmedAt, null);
    }

    /** v2 field first, v1 field as fallback. */
    public BigDecimal effectiveAmount() {
        return totalAmount != null ? totalAmount : amount;
    }
}
