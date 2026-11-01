package com.tickethub.eventshow.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;

/** event and venue of a show are immutable once created, so they are not part of this request. */
public record ShowUpdateRequest(
        @NotNull Instant startTime,
        @NotNull Instant endTime,
        @NotNull @DecimalMin(value = "0.01", message = "price must be greater than 0") BigDecimal price,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "currency must be a 3-letter uppercase code") String currency) {
}
