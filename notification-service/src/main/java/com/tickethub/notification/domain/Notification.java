package com.tickethub.notification.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;
    @Column(name = "booking_id", nullable = false)
    private Long bookingId;
    @Column(nullable = false)
    private String recipient;
    @Column(nullable = false)
    private String subject;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;
    private String error;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Notification() {
    }

    public Notification(String eventId, Long bookingId, String recipient, String subject, String body, Instant now) {
        this.eventId = eventId;
        this.bookingId = bookingId;
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
        this.status = NotificationStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markSent(Instant now) {
        this.status = NotificationStatus.SENT;
        this.error = null;
        this.updatedAt = now;
    }

    public void markFailed(String error, Instant now) {
        this.status = NotificationStatus.FAILED;
        this.error = error == null ? null : error.substring(0, Math.min(error.length(), 500));
        this.updatedAt = now;
    }

    /** Scenario 10: the mail provider failed; a delayed retry is coming. */
    public void markRetrying(String error, Instant now) {
        this.status = NotificationStatus.RETRYING;
        this.error = error == null ? null : error.substring(0, Math.min(error.length(), 500));
        this.updatedAt = now;
    }

    /** Scenario 12: rebuilt from history; deliberately NOT sent. */
    public void markReplayed(Instant now) {
        this.status = NotificationStatus.REPLAYED;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public Long getBookingId() { return bookingId; }
    public String getRecipient() { return recipient; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public NotificationStatus getStatus() { return status; }
    public String getError() { return error; }
    public Instant getCreatedAt() { return createdAt; }
}
