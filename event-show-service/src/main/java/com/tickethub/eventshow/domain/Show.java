package com.tickethub.eventshow.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/** One screening/performance of an event at a venue. Owns the ticket price (backlog ST-46). */
@Entity
@Table(name = "shows")
public class Show {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "venue_id", nullable = false)
    private Long venueId;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ShowStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Show() {
    }

    public Show(Long eventId, Long venueId, Instant startTime, Instant endTime,
                BigDecimal price, String currency, Instant now) {
        this.eventId = eventId;
        this.venueId = venueId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.price = price;
        this.currency = currency;
        this.status = ShowStatus.SCHEDULED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(Instant startTime, Instant endTime, BigDecimal price, String currency, Instant now) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.price = price;
        this.currency = currency;
        this.updatedAt = now;
    }

    public void changeStatus(ShowStatus next, Instant now) {
        this.status = next;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Long getEventId() { return eventId; }
    public Long getVenueId() { return venueId; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public BigDecimal getPrice() { return price; }
    public String getCurrency() { return currency; }
    public ShowStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
