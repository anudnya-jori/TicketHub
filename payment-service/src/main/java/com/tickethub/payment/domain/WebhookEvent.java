package com.tickethub.payment.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "webhook_events")
public class WebhookEvent {

    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected WebhookEvent() {
    }

    public WebhookEvent(String eventId, Long paymentId, Instant receivedAt) {
        this.eventId = eventId;
        this.paymentId = paymentId;
        this.receivedAt = receivedAt;
    }

    public String getEventId() { return eventId; }
    public Long getPaymentId() { return paymentId; }
}
