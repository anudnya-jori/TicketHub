package com.tickethub.payment.dto;

import com.tickethub.payment.domain.Payment;
import com.tickethub.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * providerOrderId + checkoutKeyId are what the browser needs to open Razorpay Checkout
 * (null for the simulated provider).
 */
public record PaymentResponse(Long paymentId, Long bookingId, BigDecimal amount, String currency,
                              PaymentStatus status, String failureReason, Instant createdAt,
                              String providerOrderId, String checkoutKeyId) {

    public static PaymentResponse from(Payment p) {
        return from(p, null);
    }

    public static PaymentResponse from(Payment p, String checkoutKeyId) {
        return new PaymentResponse(p.getId(), p.getBookingId(), p.getAmount(), p.getCurrency(), p.getStatus(),
                p.getFailureReason(), p.getCreatedAt(), p.getProviderOrderId(), checkoutKeyId);
    }
}