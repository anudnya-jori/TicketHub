package com.tickethub.payment.controller;

import com.tickethub.payment.dto.*;
import com.tickethub.payment.provider.SimulationHints;
import com.tickethub.payment.security.CustomerId;
import com.tickethub.payment.service.PaymentService;
import com.tickethub.payment.service.WebhookService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final WebhookService webhookService;

    public PaymentController(PaymentService paymentService, WebhookService webhookService) {
        this.paymentService = paymentService;
        this.webhookService = webhookService;
    }

    /**
     * Pay for a booking. 201 = new payment, 200 = this booking already had a payment (safe retry).
     * X-Simulate-* headers force an outcome/delay but only when payment.simulation.allow-overrides=true (dev).
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> pay(
            @CustomerId String customerId,
            @RequestHeader(value = "X-Simulate-Outcome", required = false) String simulateOutcome,
            @RequestHeader(value = "X-Simulate-Delay-Ms", required = false) Long simulateDelayMs,
            @Valid @RequestBody CreatePaymentRequest request) {
        CreatePaymentResult result = paymentService.pay(customerId, request.bookingId(),
                new SimulationHints(simulateOutcome, simulateDelayMs));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.payment());
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable Long id, @CustomerId String customerId) {
        return paymentService.getPayment(id, customerId);
    }

    /** Razorpay Checkout finished in the browser: send order id, payment id and signature. */
    @PostMapping("/{id}/verify")
    public PaymentResponse verify(@PathVariable Long id, @CustomerId String customerId,
                                  @Valid @RequestBody VerifyPaymentRequest request) {
        return paymentService.verify(customerId, id, request);
    }

    /** Provider callback. Authenticated by the HMAC signature of the raw body, not by a customer identity. */
    @PostMapping("/webhook")
    public WebhookResponse webhook(@RequestBody String rawBody,
                                   @RequestHeader(value = "X-Signature", required = false) String signature) {
        return webhookService.handle(rawBody, signature);
    }
}
