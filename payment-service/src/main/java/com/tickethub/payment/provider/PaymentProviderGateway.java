package com.tickethub.payment.provider;

import com.tickethub.payment.resilience.ResilientCaller;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.*;

/**
 * Calls the provider with a hard time limit, so a slow provider can never hang our request threads (ST-17).
 * On timeout the OUTCOME IS UNKNOWN (the provider may still charge the customer) - the caller must treat the
 * payment as PENDING, never as failed, and wait for the webhook.
 */
@Component
public class PaymentProviderGateway {

    private final PaymentProvider provider;
    private final long timeoutMs;
    private final CircuitBreaker breaker;
    private final ResilientCaller resilient;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public PaymentProviderGateway(PaymentProvider provider, ResilientCaller resilient,
                                  @Value("${payment.provider-timeout-ms:3000}") long timeoutMs) {
        this.provider = provider;
        this.timeoutMs = timeoutMs;
        this.resilient = resilient;
        this.breaker = resilient.breaker("payment-provider");
    }

    /** False while the circuit breaker is open: callers should answer 503 right away (ST-17). */
    public boolean isAvailable() {
        return breaker.getState() != CircuitBreaker.State.OPEN;
    }

    /** Provider lookup of a payment's real status (no breaker-open fast path: reconciliation just retries later). */
    public String fetchStatus(ProviderIds ids) {
        return provider.fetchStatus(ids);
    }

    public boolean verifyCheckoutSignature(String orderId, String paymentId, String signature) {
        return provider.verifyCheckoutSignature(orderId, paymentId, signature);
    }

    public String providerName() {
        return provider.name();
    }

    public ChargeOutcome charge(String reference, BigDecimal amount, String currency, SimulationHints hints) {
        try {
            // timeouts and crashes count as failures for the breaker; a normal "card declined" answer does not
            return breaker.executeSupplier(() -> chargeWithTimeout(reference, amount, currency, hints));
        } catch (CallNotPermittedException open) {
            throw new ProviderUnavailableException("Payment provider circuit breaker is open");
        }
    }

    /**
     * Refund with the same hard time limit, behind its OWN circuit breaker (a failing refund must not stop new payments).
     * A timeout is not "no refund": the caller must retry, which is safe because the provider refund is idempotent.
     */
    public RefundOutcome refund(ProviderIds ids, BigDecimal amount, String currency) {
        CircuitBreaker refundBreaker = resilient.breaker("payment-provider-refund");
        try {
            return refundBreaker.executeSupplier(() -> {
                Future<RefundOutcome> future = executor.submit(() -> provider.refund(ids, amount, currency));
                try {
                    return future.get(timeoutMs, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    throw new ProviderCallTimeoutException("Refund did not answer within " + timeoutMs + " ms");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ProviderCallTimeoutException("Interrupted while waiting for the provider");
                } catch (ExecutionException e) {
                    throw new IllegalStateException("Provider refund failed", e.getCause());
                }
            });
        } catch (CallNotPermittedException open) {
            throw new ProviderUnavailableException("Payment provider refund circuit breaker is open");
        }
    }

    private ChargeOutcome chargeWithTimeout(String reference, BigDecimal amount, String currency, SimulationHints hints) {
        Future<ChargeOutcome> future = executor.submit(() -> provider.charge(reference, amount, currency, hints));
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new ProviderCallTimeoutException("Provider did not answer within " + timeoutMs + " ms");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderCallTimeoutException("Interrupted while waiting for the provider");
        } catch (ExecutionException e) {
            throw new IllegalStateException("Provider call failed", e.getCause());
        }
    }
}
