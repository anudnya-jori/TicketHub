package com.tickethub.booking.dto;

/** status is RESERVED or BOOKED. A seat that is not listed is AVAILABLE. */
public record SeatStateResponse(Long seatId, String status) {
}
