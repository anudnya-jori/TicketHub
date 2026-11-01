package com.tickethub.payment.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Business metrics for payments (ST-26): outcomes, provider timeouts, webhook handling. */
@Component
public class PaymentMetrics {

    private final MeterRegistry registry;

    public PaymentMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** status = SUCCESS | FAILED | PENDING */
    public void payment(String status) {
        registry.counter("tickethub.payments", "status", status).increment();
    }

    /** result = APPLIED | DUPLICATE | IGNORED | REJECTED */
    public void webhook(String result) {
        registry.counter("tickethub.payment.webhooks", "result", result).increment();
    }

    public void providerUnavailable() {
        registry.counter("tickethub.payment.provider_unavailable").increment();
    }
}
