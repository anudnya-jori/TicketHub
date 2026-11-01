package com.tickethub.payment.resilience;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Fault tolerance for calls to other services (ST-16, ST-17, ST-18).
 *
 *  RETRY            only for failures that may be temporary (connection problem, timeout, HTTP 5xx), only for
 *                   calls that are SAFE to repeat (reads). Waits a little between attempts.
 *  CIRCUIT BREAKER  if a dependency keeps failing, stop calling it for a while ("open") and fail fast, so threads are
 *                   not wasted waiting and the sick service gets room to recover. Later a few trial calls ("half-open")
 *                   decide whether to close it again.
 *  A 4xx answer (e.g. 404 "show not found") is a normal business answer: it is never retried and never counts as a failure.
 *
 * Order for reads: Retry( CircuitBreaker( call ) ) - every attempt is measured by the breaker.
 */
@Component
public class ResilientCaller {

    private static final Logger log = LoggerFactory.getLogger(ResilientCaller.class);

    private final CircuitBreakerRegistry breakers;
    private final RetryRegistry retries;
    @Nullable
    private final MeterRegistry meters;

    public ResilientCaller(@Value("${resilience.retry.max-attempts:3}") int maxAttempts,
                           @Value("${resilience.retry.wait-ms:200}") long waitMs,
                           @Value("${resilience.circuit-breaker.sliding-window-size:10}") int windowSize,
                           @Value("${resilience.circuit-breaker.minimum-calls:5}") int minimumCalls,
                           @Value("${resilience.circuit-breaker.failure-rate-threshold:50}") float failureRate,
                           @Value("${resilience.circuit-breaker.wait-open-ms:10000}") long waitOpenMs,
                           @Nullable MeterRegistry meters) {
        this.meters = meters;
        this.breakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(windowSize)
                .minimumNumberOfCalls(minimumCalls)
                .failureRateThreshold(failureRate)
                .waitDurationInOpenState(Duration.ofMillis(waitOpenMs))
                .permittedNumberOfCallsInHalfOpenState(2)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordExceptions(ResourceAccessException.class, HttpServerErrorException.class,
                        com.tickethub.payment.provider.ProviderCallTimeoutException.class, IllegalStateException.class)
                .ignoreExceptions(HttpClientErrorException.class)
                .build());
        this.retries = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .waitDuration(Duration.ofMillis(waitMs))
                .retryOnException(e -> e instanceof ResourceAccessException || e instanceof HttpServerErrorException)
                .build());
    }

    /** For READS (safe to repeat): retry + circuit breaker. Throws CallNotPermittedException when the breaker is open. */
    public <T> T read(String dependency, Supplier<T> call) {
        Supplier<T> guarded = CircuitBreaker.decorateSupplier(breaker(dependency), call);
        Retry retry = retries.retry(dependency);
        return Retry.decorateSupplier(retry, guarded).get();
    }

    /** For calls that must NOT be repeated blindly: circuit breaker only (no retry). */
    public <T> T once(String dependency, Supplier<T> call) {
        return CircuitBreaker.decorateSupplier(breaker(dependency), call).get();
    }

    public CircuitBreaker breaker(String dependency) {
        boolean isNew = breakers.getAllCircuitBreakers().stream().noneMatch(b -> b.getName().equals(dependency));
        CircuitBreaker cb = breakers.circuitBreaker(dependency);
        if (isNew) {
            cb.getEventPublisher().onStateTransition(e -> log.warn("CIRCUIT_BREAKER {} {} -> {}", dependency,
                    e.getStateTransition().getFromState(), e.getStateTransition().getToState()));
            if (meters != null) {
                Gauge.builder("resilience.circuitbreaker.state", cb, c -> switch (c.getState()) {
                            case CLOSED -> 0;
                            case HALF_OPEN -> 1;
                            case OPEN, FORCED_OPEN -> 2;
                            default -> 3;
                        })
                        .description("0=closed, 1=half-open, 2=open")
                        .tag("name", dependency)
                        .register(meters);
            }
        }
        return cb;
    }

    public static boolean isOpenCircuit(Throwable t) {
        return t instanceof CallNotPermittedException;
    }
}
