package com.tickethub.booking.dto;

import com.tickethub.booking.domain.Booking;
import com.tickethub.booking.domain.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** refundStatus (Phase 2): NONE | REQUESTED | REFUNDED - where the money of a rejected/cancelled booking stands. */
public record BookingResponse(Long bookingId, String customerId, Long showId, List<Long> seatIds, BigDecimal amount,
                              String currency, BookingStatus status, Instant expiryAt, Instant createdAt,
                              String refundStatus) {

    public static BookingResponse from(Booking b, List<Long> seatIds) {
        return new BookingResponse(b.getId(), b.getCustomerId(), b.getShowId(), seatIds, b.getAmount(),
                b.getCurrency(), b.getStatus(), b.getExpiryAt(), b.getCreatedAt(), b.getRefundStatus().name());
    }
}
