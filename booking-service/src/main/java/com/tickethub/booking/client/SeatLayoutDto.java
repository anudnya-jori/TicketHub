package com.tickethub.booking.client;

public record SeatLayoutDto(Long id, Long venueId, String row, int number, String category) {
}
