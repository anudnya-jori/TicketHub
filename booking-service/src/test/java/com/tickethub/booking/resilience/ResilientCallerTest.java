package com.tickethub.booking.resilience;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ResilientCallerTest {

    // 3 attempts, 1 ms wait, window 4, judge after 4 calls, open at 50% failures, stay open 150 ms
    private ResilientCaller caller() {
        return new ResilientCaller(3, 1, 4, 4, 50f, 150, null);
    }

    @Test
    void aTemporaryFailureIsRetriedAndThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        String result = caller().read("dep", () -> {
            if (calls.incrementAndGet() < 3) {
                throw new ResourceAccessException("connection refused");
            }
            return "ok";
        });
        assertEquals("ok", result);
        assertEquals(3, calls.get());
    }

    @Test
    void retriesStopAfterTheConfiguredAttempts() {
        AtomicInteger calls = new AtomicInteger();
        assertThrows(HttpServerErrorException.class, () -> caller().read("dep", () -> {
            calls.incrementAndGet();
            throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);
        }));
        assertEquals(3, calls.get());
    }

    @Test
    void aBusinessAnswer4xxIsNeverRetriedAndNeverCountsAsAFailure() {
        ResilientCaller caller = caller();
        AtomicInteger calls = new AtomicInteger();
        for (int i = 0; i < 10; i++) {
            assertThrows(HttpClientErrorException.class, () -> caller.read("dep", () -> {
                calls.incrementAndGet();
                throw HttpClientErrorException.create(HttpStatus.NOT_FOUND, "nf", null, null, null);
            }));
        }
        assertEquals(10, calls.get());                                   // one call each, no retries
        assertEquals("CLOSED", caller.breaker("dep").getState().name()); // breaker untouched
    }

    @Test
    void whenADependencyKeepsFailingTheBreakerOpensAndFailsFast() {
        ResilientCaller caller = caller();
        AtomicInteger calls = new AtomicInteger();
        for (int i = 0; i < 2; i++) {
            assertThrows(ResourceAccessException.class, () -> caller.once("dep", () -> {
                calls.incrementAndGet();
                throw new ResourceAccessException("down");
            }));
        }
        for (int i = 0; i < 2; i++) {
            assertThrows(ResourceAccessException.class, () -> caller.once("dep", () -> {
                calls.incrementAndGet();
                throw new ResourceAccessException("down");
            }));
        }
        int before = calls.get();
        assertThrows(CallNotPermittedException.class, () -> caller.once("dep", () -> "never called"));
        assertEquals(before, calls.get());                               // the dependency was NOT called
        assertEquals("OPEN", caller.breaker("dep").getState().name());
    }

    @Test
    void afterTheWaitTheBreakerLetsTrialCallsThroughAndClosesOnSuccess() throws Exception {
        ResilientCaller caller = caller();
        for (int i = 0; i < 4; i++) {
            assertThrows(ResourceAccessException.class, () -> caller.once("dep", () -> {
                throw new ResourceAccessException("down");
            }));
        }
        assertEquals("OPEN", caller.breaker("dep").getState().name());
        Thread.sleep(400);
        assertEquals("ok", caller.once("dep", () -> "ok"));
        assertEquals("ok", caller.once("dep", () -> "ok"));
        assertEquals("CLOSED", caller.breaker("dep").getState().name());
    }
}
