package com.tickethub.eventshow.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusTransitionTest {

    @Test
    void eventLifecycleGoesForwardOnly() {
        assertTrue(EventStatus.DRAFT.canTransitionTo(EventStatus.PUBLISHED));
        assertTrue(EventStatus.PUBLISHED.canTransitionTo(EventStatus.CANCELLED));
        assertFalse(EventStatus.DRAFT.canTransitionTo(EventStatus.CANCELLED));
        assertFalse(EventStatus.PUBLISHED.canTransitionTo(EventStatus.DRAFT));
        assertFalse(EventStatus.CANCELLED.canTransitionTo(EventStatus.PUBLISHED));
    }

    @Test
    void showCanBeOpenedOnlyFromScheduled() {
        assertTrue(ShowStatus.SCHEDULED.canTransitionTo(ShowStatus.OPEN));
        assertFalse(ShowStatus.OPEN.canTransitionTo(ShowStatus.SCHEDULED));
        assertFalse(ShowStatus.CANCELLED.canTransitionTo(ShowStatus.OPEN));
        assertFalse(ShowStatus.COMPLETED.canTransitionTo(ShowStatus.OPEN));
    }
}
