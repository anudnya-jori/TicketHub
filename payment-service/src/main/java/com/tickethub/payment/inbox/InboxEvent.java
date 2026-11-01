package com.tickethub.payment.inbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One consumed event id (Scenarios 2 and 4). */
@Entity
@Table(name = "inbox_events")
public class InboxEvent {

    @Id
    @Column(name = "event_id", length = 100)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected InboxEvent() {
    }

    public InboxEvent(String eventId, String eventType, Long bookingId, Instant receivedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.bookingId = bookingId;
        this.receivedAt = receivedAt;
    }

    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public Long getBookingId() { return bookingId; }
    public Instant getReceivedAt() { return receivedAt; }
}
