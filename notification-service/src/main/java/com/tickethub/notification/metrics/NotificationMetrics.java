package com.tickethub.notification.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class NotificationMetrics {
    private final MeterRegistry registry;

    public NotificationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** result = SENT | FAILED | DUPLICATE */
    public void notification(String result) {
        registry.counter("tickethub.notifications", "result", result).increment();
    }
}
