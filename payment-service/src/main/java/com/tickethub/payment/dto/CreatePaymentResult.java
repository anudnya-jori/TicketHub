package com.tickethub.payment.dto;

/** created=false means an existing payment for this booking was returned (idempotent retry). */
public record CreatePaymentResult(PaymentResponse payment, boolean created) {
}
