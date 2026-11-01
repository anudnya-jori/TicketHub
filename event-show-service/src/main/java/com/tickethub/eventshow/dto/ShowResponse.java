package com.tickethub.eventshow.dto;

import com.tickethub.eventshow.domain.EventStatus;
import com.tickethub.eventshow.domain.Show;
import com.tickethub.eventshow.domain.ShowStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** eventStatus is included so Booking can check "show OPEN and event PUBLISHED" with a single call. */
public record ShowResponse(Long id, Long eventId, EventStatus eventStatus, Long venueId,
                           Instant startTime, Instant endTime, BigDecimal price, String currency,
                           ShowStatus status) {

    public static ShowResponse from(Show s, EventStatus eventStatus) {
        return new ShowResponse(s.getId(), s.getEventId(), eventStatus, s.getVenueId(), s.getStartTime(),
                s.getEndTime(), s.getPrice(), s.getCurrency(), s.getStatus());
    }
}
