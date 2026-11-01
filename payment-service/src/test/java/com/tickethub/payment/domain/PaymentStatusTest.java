package com.tickethub.payment.domain;

import com.tickethub.payment.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PaymentStatusTest {

    @Test
    void initiatedCanReachEveryOutcomeAndPendingCanStillResolve() {
        assertTrue(PaymentStatus.INITIATED.canTransitionTo(PaymentStatus.SUCCESS));
        assertTrue(PaymentStatus.INITIATED.canTransitionTo(PaymentStatus.FAILED));
        assertTrue(PaymentStatus.INITIATED.canTransitionTo(PaymentStatus.PENDING));
        assertTrue(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.SUCCESS));
        assertTrue(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.FAILED));
    }

    @Test
    void finalStatesNeverMoveBackwards() {
        assertEquals(java.util.Set.of(PaymentStatus.REFUNDED), PaymentStatus.SUCCESS.allowedNext());   // the only exit: refund
        assertTrue(PaymentStatus.FAILED.allowedNext().isEmpty());
        assertTrue(PaymentStatus.REFUNDED.allowedNext().isEmpty());
        assertFalse(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.INITIATED));
    }

    @Test
    void entityRejectsIllegalTransition() {
        Payment p = new Payment(1L, "c", new BigDecimal("10.00"), "INR", "SIMULATED", "ref", Instant.now());
        p.moveTo(PaymentStatus.SUCCESS, null, Instant.now());
        ApiException ex = assertThrows(ApiException.class, () -> p.moveTo(PaymentStatus.FAILED, "x", Instant.now()));
        assertEquals("INVALID_STATE_TRANSITION", ex.getCode());
    }

    @Test
    void aSecondRefundIsRefusedByTheStateMachineAndEveryChangeBumpsTheVersion() {
        Payment p = new Payment(1L, "c", new BigDecimal("10.00"), "INR", "SIMULATED", "ref", Instant.now());
        assertEquals(0, p.getAggregateVersion());
        p.moveTo(PaymentStatus.SUCCESS, null, Instant.now());
        p.markRefunded("rfnd_ref", Instant.now());
        assertEquals(2, p.getAggregateVersion());
        assertEquals("rfnd_ref", p.getRefundId());
        assertThrows(ApiException.class, () -> p.markRefunded("rfnd_other", Instant.now()));
    }
}
