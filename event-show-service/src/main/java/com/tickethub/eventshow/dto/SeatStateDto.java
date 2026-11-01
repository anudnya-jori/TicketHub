package com.tickethub.eventshow.dto;

/** Event/Show's own copy of the shape Booking returns (never share classes between services). */
public record SeatStateDto(Long seatId, String status) {
}
