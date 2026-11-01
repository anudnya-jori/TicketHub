package com.tickethub.eventshow.domain;

import jakarta.persistence.*;

/**
 * A PHYSICAL seat in a venue. It belongs to the venue permanently, not to a show.
 * The same seat can be sold for many different shows (one booking at a time per show).
 */
@Entity
@Table(name = "seats")
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "venue_id", nullable = false)
    private Long venueId;

    @Column(name = "row_label", nullable = false, length = 10)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false)
    private int seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SeatCategory category;

    protected Seat() {
    }

    public Seat(Long venueId, String rowLabel, int seatNumber, SeatCategory category) {
        this.venueId = venueId;
        this.rowLabel = rowLabel;
        this.seatNumber = seatNumber;
        this.category = category;
    }

    public Long getId() { return id; }
    public Long getVenueId() { return venueId; }
    public String getRowLabel() { return rowLabel; }
    public int getSeatNumber() { return seatNumber; }
    public SeatCategory getCategory() { return category; }
}
