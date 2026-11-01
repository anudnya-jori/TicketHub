package com.tickethub.eventshow.dto;

import com.tickethub.eventshow.domain.SeatCategory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SeatRequest(
        @NotBlank @Size(max = 10) String row,
        @NotNull @Min(1) Integer number,
        SeatCategory category) {
}
