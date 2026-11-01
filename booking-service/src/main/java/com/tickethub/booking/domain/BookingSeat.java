package com.tickethub.booking.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** One seat held for one show by one booking. Rows are never deleted: release = status RELEASED. */
@Entity
@Table(name = "booking_seats")
public class BookingSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "show_id", nullable = false)
    private Long showId;

    @Column(name = "seat_id", nullable = false)
    private Long seatId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatHoldStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected BookingSeat() {
    }

    public BookingSeat(Long bookingId, Long showId, Long seatId, Instant now) {
        this.bookingId = bookingId;
        this.showId = showId;
        this.seatId = seatId;
        this.status = SeatHoldStatus.RESERVED;
        this.createdAt = now;
    }

    public Long getId() { return id; }
    public Long getBookingId() { return bookingId; }
    public Long getShowId() { return showId; }
    public Long getSeatId() { return seatId; }
    public SeatHoldStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
