package com.tickethub.payment.dto;

import jakarta.validation.constraints.NotNull;

/** Only the booking id. The AMOUNT is never taken from the client; it comes from the Booking Service. */
public record CreatePaymentRequest(@NotNull Long bookingId) {
}
