package com.tickethub.eventshow.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AddSeatsRequest(@NotEmpty @Size(max = 1000) List<@Valid SeatRequest> seats) {
}
