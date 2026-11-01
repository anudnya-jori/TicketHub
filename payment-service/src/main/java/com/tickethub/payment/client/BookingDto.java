package com.tickethub.payment.client;

import java.math.BigDecimal;
import java.time.Instant;

/** Payment's own view of a booking (no class is shared with the Booking Service). */
public record BookingDto(Long bookingId, String customerId, BigDecimal amount, String currency, String status,
                         Instant expiryAt) {
}
