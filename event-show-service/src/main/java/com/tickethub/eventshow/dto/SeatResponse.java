package com.tickethub.eventshow.dto;

import com.tickethub.eventshow.domain.Seat;
import com.tickethub.eventshow.domain.SeatCategory;

public record SeatResponse(Long id, Long venueId, String row, int number, SeatCategory category) {

    public static SeatResponse from(Seat s) {
        return new SeatResponse(s.getId(), s.getVenueId(), s.getRowLabel(), s.getSeatNumber(), s.getCategory());
    }
}
