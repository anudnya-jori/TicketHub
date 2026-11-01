package com.tickethub.eventshow.domain;

/** What the customer sees per seat. Computed by merging layout (here) with Booking's data. Never stored here. */
public enum SeatState { AVAILABLE, RESERVED, BOOKED }
