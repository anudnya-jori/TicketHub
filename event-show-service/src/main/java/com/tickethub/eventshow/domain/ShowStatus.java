package com.tickethub.eventshow.domain;

import java.util.Set;

/**
 * SCHEDULED -> OPEN -> (CANCELLED). SCHEDULED may also be CANCELLED.
 * SOLD_OUT and COMPLETED exist in the model but are not driven by anything in v1.
 */
public enum ShowStatus {
    SCHEDULED, OPEN, SOLD_OUT, CANCELLED, COMPLETED;

    public Set<ShowStatus> allowedNext() {
        return switch (this) {
            case SCHEDULED -> Set.of(OPEN, CANCELLED);
            case OPEN -> Set.of(SOLD_OUT, CANCELLED, COMPLETED);
            case SOLD_OUT -> Set.of(CANCELLED, COMPLETED);
            case CANCELLED, COMPLETED -> Set.of();
        };
    }

    public boolean canTransitionTo(ShowStatus next) {
        return allowedNext().contains(next);
    }
}
