package com.tickethub.payment.provider;

/** Everything we know about a payment at the provider: our reference, Razorpay order id, Razorpay payment id. */
public record ProviderIds(String reference, String orderId, String paymentId) {
}