package com.tickethub.eventshow.domain;

import java.util.Set;

/** DRAFT -> PUBLISHED -> CANCELLED. Not reversible. */
public enum EventStatus {
    DRAFT, PUBLISHED, CANCELLED;

    public Set<EventStatus> allowedNext() {
        return switch (this) {
            case DRAFT -> Set.of(PUBLISHED);
            case PUBLISHED -> Set.of(CANCELLED);
            case CANCELLED -> Set.of();
        };
    }

    public boolean canTransitionTo(EventStatus next) {
        return allowedNext().contains(next);
    }
}
