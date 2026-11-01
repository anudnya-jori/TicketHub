package com.tickethub.eventshow.dto;

import com.tickethub.eventshow.domain.SeatCategory;
import com.tickethub.eventshow.domain.SeatState;

public record SeatAvailabilityResponse(Long seatId, String row, int number, SeatCategory category, SeatState status) {
}
