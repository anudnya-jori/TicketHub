package com.tickethub.booking.dto;

import java.time.Instant;
import java.util.List;

/** Scenario 7. state: PAYMENT_PENDING | CONFIRMED | FAILED | COMPENSATING | COMPENSATED. steps = events received/sent. */
public record SagaView(Long bookingId, String state, String bookingStatus, String refundStatus, String why,
                       long aggregateVersion, List<Step> steps) {
    public record Step(String direction, String eventType, String eventId, Instant at) {
    }
}
