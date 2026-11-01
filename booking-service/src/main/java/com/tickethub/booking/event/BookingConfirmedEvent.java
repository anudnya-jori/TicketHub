package com.tickethub.booking.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * EVENT CONTRACT (ST-19): "this booking is confirmed". Topic booking.confirmed, key = bookingId, JSON.
 *
 *   eventId           stable per booking ("booking-confirmed-{bookingId}") so consumers can detect duplicates
 *   bookingId         the booking (also the Kafka message key)
 *   customerId        who booked
 *   recipient         where the confirmation email goes
 *   showId, seatIds   what was booked
 *   amount, currency  price snapshot paid
 *   confirmedAt       when it was confirmed (UTC)
 * Phase 2 fields (additive: Notification ignores unknown fields, so old consumers keep working - Scenario 13):
 *   eventType         "BookingConfirmed"
 *   version           schema version of this contract (1)
 *   aggregateVersion  the booking's version when this was written (Scenario 6)
 *   totalAmount       version 2: the new name of amount (both are written during the migration)
 */
public record BookingConfirmedEvent(String eventId, Long bookingId, String customerId, String recipient,
                                    Long showId, List<Long> seatIds, BigDecimal amount, String currency,
                                    Instant confirmedAt, String eventType, int version, long aggregateVersion,
                                    BigDecimal totalAmount) {

    public static final String TYPE = "BookingConfirmed";
    /** Schema version 2 (Scenario 13): adds totalAmount. amount is KEPT with the same value so version-1 consumers keep working;
     *  it can be dropped once every consumer reads totalAmount. */
    public static final int VERSION = 2;

    /** Phase 1 shape (no aggregate version). */
    public BookingConfirmedEvent(String eventId, Long bookingId, String customerId, String recipient,
                                 Long showId, List<Long> seatIds, BigDecimal amount, String currency,
                                 Instant confirmedAt) {
        this(eventId, bookingId, customerId, recipient, showId, seatIds, amount, currency, confirmedAt, TYPE, VERSION, 0L, amount);
    }

    public static String idFor(Long bookingId) {
        return "booking-confirmed-" + bookingId;
    }
}
