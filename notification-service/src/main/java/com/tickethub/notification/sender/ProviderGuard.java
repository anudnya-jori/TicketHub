package com.tickethub.notification.sender;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Scenario 10: protection around the email provider. A circuit breaker stops hammering a dead mail server (calls fail
 * fast and are retried later through the retry topics) and a send-rate limit keeps us under the provider's quota.
 */
@Component
public class ProviderGuard {

    private final CircuitBreaker breaker;
    private final long minIntervalNanos;
    private long nextSlot = System.nanoTime();

    public ProviderGuard(@Value("${notification.provider.max-per-second:50}") int maxPerSecond,
                         @Value("${notification.provider.breaker.window:10}") int window,
                         @Value("${notification.provider.breaker.min-calls:5}") int minCalls,
                         @Value("${notification.provider.breaker.open-ms:10000}") long openMs) {
        this.minIntervalNanos = maxPerSecond <= 0 ? 0 : 1_000_000_000L / maxPerSecond;
        this.breaker = CircuitBreaker.of("email-provider", CircuitBreakerConfig.custom()
                .slidingWindowSize(window).minimumNumberOfCalls(minCalls).failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMillis(openMs)).build());
    }

    public void send(EmailSender sender, String to, String subject, String body) {
        throttle();
        breaker.executeRunnable(() -> sender.send(to, subject, body));
    }

    public CircuitBreaker.State state() {
        return breaker.getState();
    }

    private synchronized void throttle() {
        if (minIntervalNanos == 0) {
            return;
        }
        long now = System.nanoTime();
        long wait = nextSlot - now;
        nextSlot = Math.max(now, nextSlot) + minIntervalNanos;
        if (wait > 0) {
            try {
                Thread.sleep(wait / 1_000_000, (int) (wait % 1_000_000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
