package com.tickethub.eventshow.dto;

import java.util.List;

public record ShowSeatsResponse(Long showId, List<SeatAvailabilityResponse> seats) {
}
