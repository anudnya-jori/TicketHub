package com.tickethub.booking.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Only what the client may decide: which show and which seats.
 * Amount, status, expiry and customer identity are never accepted from the client.
 */
public record CreateBookingRequest(
        @NotNull Long showId,
        @NotEmpty @Size(max = 20) List<@NotNull Long> seatIds) {
}
