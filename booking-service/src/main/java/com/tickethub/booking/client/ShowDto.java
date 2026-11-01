package com.tickethub.booking.client;

import java.math.BigDecimal;
import java.time.Instant;

/** Booking's OWN view of a show, deserialised from Event/Show's JSON (no shared classes between services). */
public record ShowDto(Long id, Long eventId, String eventStatus, Long venueId, Instant startTime, Instant endTime,
                      BigDecimal price, String currency, String status) {
}
