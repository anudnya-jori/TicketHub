package com.tickethub.booking.domain;

import com.tickethub.booking.exception.ApiException;

/** The single guard every status change goes through ("status changes only along the Blueprint model"). */
public final class BookingStateMachine {

    private BookingStateMachine() {
    }

    public static void assertTransition(BookingStatus from, BookingStatus to) {
        if (!from.canTransitionTo(to)) {
            throw ApiException.conflict("INVALID_STATE_TRANSITION",
                    "Booking cannot go from " + from + " to " + to + ".");
        }
    }
}
