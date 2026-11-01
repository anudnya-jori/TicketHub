package com.tickethub.payment.dto;

import jakarta.validation.constraints.NotBlank;

/** The three values Razorpay Checkout hands to the browser after a successful payment. */
public record VerifyPaymentRequest(@NotBlank String razorpayOrderId, @NotBlank String razorpayPaymentId,
                                   @NotBlank String razorpaySignature) {
}