package com.tickethub.booking.dto;

/** created = true for a new booking (HTTP 201), false when an idempotent retry returned the original (HTTP 200). */
public record CreateBookingResult(BookingResponse booking, boolean created) {
}
