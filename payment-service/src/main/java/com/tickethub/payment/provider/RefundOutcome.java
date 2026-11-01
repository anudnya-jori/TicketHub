package com.tickethub.payment.provider;

/** What the provider answered to a refund request. refundId is the provider's id for the refund. */
public record RefundOutcome(String refundId) {
}
