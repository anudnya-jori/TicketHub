package com.tickethub.payment.provider;

import java.math.BigDecimal;

/**
 * The seam to the outside world. Today: {@link SimulatedPaymentProvider}. A real provider sandbox
 * (Razorpay/Stripe test mode) is another implementation of this interface; nothing else changes.
 */
public interface PaymentProvider {
    String name();

    ChargeOutcome charge(String providerReference, BigDecimal amount, String currency, SimulationHints hints);

    /**
     * Refunds a captured payment (Scenario 5). MUST be idempotent per providerReference: asking twice returns the
     * same refund, never a second one (Razorpay's Refunds API gives the same guarantee through its refund id).
     */
    RefundOutcome refund(ProviderIds ids, BigDecimal amount, String currency);
    /**
     * Source of truth for money (Scenarios 8 and 9): asks the provider what really happened to a payment.
     * Razorpay equivalent: GET /payments/{id}. Returns SUCCESS, FAILED or PENDING.
     */
    String fetchStatus(ProviderIds ids);

    /** Razorpay Checkout proof of payment (HMAC of order_id|payment_id). The simulated provider has no checkout. */
    default boolean verifyCheckoutSignature(String orderId, String paymentId, String signature) {
        return false;
    }
}
