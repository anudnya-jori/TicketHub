package com.tickethub.booking.domain;

import java.util.Set;

/** State of one seat held by one booking. "AVAILABLE" is not stored: it means no active hold exists. */
public enum SeatHoldStatus {
    RESERVED, BOOKED, RELEASED;

    /** The holds that block other customers (matches the partial unique index in the database). */
    public static final Set<SeatHoldStatus> ACTIVE = Set.of(RESERVED, BOOKED);
}
