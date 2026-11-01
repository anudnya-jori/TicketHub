package com.tickethub.payment.provider;

/** What the payment provider answered. awaitingCustomer = an order was created; the customer pays in Checkout. */
public record ChargeOutcome(boolean success, String failureReason, boolean awaitingCustomer,
                            String providerOrderId, String providerPaymentId) {

    public ChargeOutcome(boolean success, String failureReason) {
        this(success, failureReason, false, null, null);
    }

    public static ChargeOutcome ok() { return new ChargeOutcome(true, null); }

    public static ChargeOutcome declined(String reason) { return new ChargeOutcome(false, reason); }

    public static ChargeOutcome awaitingCustomer(String orderId) {
        return new ChargeOutcome(false, null, true, orderId, null);
    }
}