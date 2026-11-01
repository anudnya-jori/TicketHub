package com.tickethub.payment.service;

import com.tickethub.payment.client.BookingClient;
import com.tickethub.payment.client.BookingDto;
import com.tickethub.payment.domain.Payment;
import com.tickethub.payment.domain.PaymentStatus;
import com.tickethub.payment.dto.CreatePaymentResult;
import com.tickethub.payment.dto.PaymentResponse;
import com.tickethub.payment.event.PaymentEvent;
import com.tickethub.payment.outbox.OutboxService;
import com.tickethub.payment.exception.ApiException;
import com.tickethub.payment.metrics.PaymentMetrics;
import com.tickethub.payment.provider.ChargeOutcome;
import com.tickethub.payment.provider.PaymentProviderGateway;
import com.tickethub.payment.provider.ProviderCallTimeoutException;
import com.tickethub.payment.provider.ProviderUnavailableException;
import com.tickethub.payment.provider.SimulationHints;
import com.tickethub.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import com.tickethub.payment.dto.VerifyPaymentRequest;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;


/**
 * Payment flow (ST-20..ST-24, ST-54, ST-55). No @Transactional around the whole thing on purpose:
 * we must never hold a database transaction open while waiting for a slow provider or another service.
 * Each save is its own short transaction.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final BookingClient bookingClient;
    private final PaymentProviderGateway gateway;
    private final Clock clock;
    private final PaymentMetrics metrics;
    private final OutboxService outbox;
    private final TransactionTemplate tx;
    private final String eventsTopic;
    private final String razorpayKeyId;

    public PaymentService(PaymentRepository paymentRepository, BookingClient bookingClient,
                          PaymentProviderGateway gateway, Clock clock, PaymentMetrics metrics,
                          OutboxService outbox, TransactionTemplate tx,
                          @Value("${payment.events.topic}") String eventsTopic,
                          @Value("${payment.razorpay.key-id:}") String razorpayKeyId
                          ) {
        this.paymentRepository = paymentRepository;
        this.bookingClient = bookingClient;
        this.gateway = gateway;
        this.clock = clock;
        this.metrics = metrics;
        this.outbox = outbox;
        this.tx = tx;
        this.eventsTopic = eventsTopic;
        this.razorpayKeyId = razorpayKeyId;
    }

    public CreatePaymentResult pay(String customerId, Long bookingId, SimulationHints hints) {
        // 1. Idempotency: a booking is paid for at most once.
        Payment existing = paymentRepository.findByBookingId(bookingId).orElse(null);
        if (existing != null) {
            assertOwner(existing, customerId);
            return new CreatePaymentResult(PaymentResponse.from(existing), false);
        }

        // 2. The Booking Service is the source of truth for amount, owner and state.
        BookingDto booking = bookingClient.getBooking(bookingId);
        if (!customerId.equals(booking.customerId())) {
            throw ApiException.notFound("BOOKING_NOT_FOUND", "Booking " + bookingId + " not found.");
        }
        if (!"PENDING_PAYMENT".equals(booking.status())) {
            throw ApiException.conflict("BOOKING_NOT_PAYABLE",
                    "Booking is " + booking.status() + " and cannot be paid.");
        }
        if (booking.expiryAt() != null && !booking.expiryAt().isAfter(clock.instant())) {
            throw ApiException.conflict("BOOKING_EXPIRED", "The reservation has expired. Please book again.");
        }

        // 2b. Circuit breaker: if the provider has been failing, answer 503 immediately, before anything is recorded.
        if (!gateway.isAvailable()) {
            throw providerUnavailable();
        }

        // 3. Record the attempt FIRST (the unique constraint settles any race between two requests).
        String reference = "pay_" + UUID.randomUUID();
        Payment payment;
        try {
            payment = paymentRepository.saveAndFlush(new Payment(bookingId, customerId, booking.amount(),
                    booking.currency(), gateway.providerName(), reference, clock.instant()));
        } catch (DataIntegrityViolationException race) {
            Payment winner = paymentRepository.findByBookingId(bookingId).orElseThrow(() -> race);
            assertOwner(winner, customerId);
            return new CreatePaymentResult(PaymentResponse.from(winner, razorpayKeyId), false);
        }
        MDC.put("paymentId", String.valueOf(payment.getId()));
        MDC.put("bookingId", String.valueOf(bookingId));
        try {
            log.info("PAYMENT_INITIATED bookingId={} paymentId={} amount={} {}", bookingId, payment.getId(),
                    booking.amount(), booking.currency());

            // 4. Call the provider with a hard timeout.
            ChargeOutcome outcome;
            try {
                outcome = gateway.charge(reference, booking.amount(), booking.currency(), hints);
            } catch (ProviderUnavailableException open) {
                // breaker opened between the check and the call: nothing was charged, so forget the attempt
                paymentRepository.delete(payment);
                throw providerUnavailable();
            } catch (ProviderCallTimeoutException timeout) {
                payment.moveTo(PaymentStatus.PENDING, "PROVIDER_TIMEOUT", clock.instant());
                paymentRepository.save(payment);
                metrics.payment("PENDING");
                log.warn("PAYMENT_PENDING paymentId={} (provider timeout, waiting for webhook)", payment.getId());
                throw ApiException.gatewayTimeout("PAYMENT_PROVIDER_TIMEOUT",
                        "The payment provider did not answer in time. Your payment is PENDING (id "
                                + payment.getId() + "); check its status shortly. You have not been charged twice.");
            }

            // 4b. Razorpay: an ORDER was created, nothing is charged yet. The customer pays in Checkout and the
            //     browser calls POST /payments/{id}/verify. Until then the payment waits as PENDING.
            if (outcome.awaitingCustomer()) {
                payment.recordProviderIds(outcome.providerOrderId(), null);
                payment.moveTo(PaymentStatus.PENDING, "AWAITING_CUSTOMER", clock.instant());
                payment = paymentRepository.save(payment);
                metrics.payment("PENDING");
                log.info("PAYMENT_AWAITING_CUSTOMER paymentId={} order={}", payment.getId(), outcome.providerOrderId());
                return new CreatePaymentResult(PaymentResponse.from(payment, razorpayKeyId), true);
            }

            // 5. Final status AND the event for Booking, in ONE transaction (outbox). No REST callback to Booking:
            //    if Booking is down, the event simply waits in Kafka (Scenario 4).
            if (outcome.success()) {
                recordOutcome(payment, PaymentStatus.SUCCESS, null);
            } else {
                recordOutcome(payment, PaymentStatus.FAILED, outcome.failureReason());
            }
            return new CreatePaymentResult(PaymentResponse.from(payment, razorpayKeyId), true);
        } finally {
            MDC.remove("paymentId");
            MDC.remove("bookingId");
        }
    }

    public PaymentResponse verify(String customerId, Long paymentId, VerifyPaymentRequest request) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> ApiException.notFound("PAYMENT_NOT_FOUND", "Payment " + paymentId + " not found."));
        assertOwner(payment, customerId);
        if (payment.getStatus().isFinal()) {
            return PaymentResponse.from(payment, razorpayKeyId);
        }
        if (!request.razorpayOrderId().equals(payment.getProviderOrderId())) {
            throw ApiException.badRequest("ORDER_MISMATCH", "This order does not belong to payment " + paymentId + ".");
        }
        if (!gateway.verifyCheckoutSignature(request.razorpayOrderId(), request.razorpayPaymentId(),
                request.razorpaySignature())) {
            throw ApiException.unauthorized("INVALID_SIGNATURE", "The checkout signature is invalid.");
        }
        payment.recordProviderIds(null, request.razorpayPaymentId());
        recordOutcome(payment, PaymentStatus.SUCCESS, null);
        return PaymentResponse.from(payment, razorpayKeyId);
    }

    public PaymentResponse getPayment(Long paymentId, String customerId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> ApiException.notFound("PAYMENT_NOT_FOUND", "Payment " + paymentId + " not found."));
        assertOwner(payment, customerId);
        return PaymentResponse.from(payment, razorpayKeyId);
    }

    /**
     * Moves a payment to SUCCESS or FAILED and writes PaymentSucceeded / PaymentFailed to the outbox in the SAME
     * transaction (Scenarios 3 and 4). Used by the pay flow and by webhooks / reconciliation of a PENDING payment.
     */
    public void recordOutcome(Payment payment, PaymentStatus status, String reason) {
        tx.executeWithoutResult(s -> {
            payment.moveTo(status, reason, clock.instant());
            Payment saved = paymentRepository.save(payment);
            enqueue(saved, status == PaymentStatus.SUCCESS ? PaymentEvent.SUCCEEDED : PaymentEvent.FAILED, reason);
        });
        metrics.payment(status.name());
        log.info("PAYMENT_{} paymentId={} bookingId={}", status, payment.getId(), payment.getBookingId());
    }

    /** Must be called inside a transaction (the outbox insert is MANDATORY-propagation). */
    public void enqueue(Payment payment, String eventType, String reason) {
        String eventId = PaymentEvent.idFor(eventType, payment.getId());
        outbox.enqueue(eventsTopic, eventId, eventType, payment.getBookingId(), new PaymentEvent(eventId, eventType, 1,
                clock.instant(), payment.getBookingId(), payment.getAggregateVersion(), payment.getId(),
                payment.getAmount(), payment.getCurrency(), reason));
    }

    private ApiException providerUnavailable() {
        metrics.providerUnavailable();
        return ApiException.unavailable("PAYMENT_PROVIDER_UNAVAILABLE",
                "The payment provider is temporarily unavailable. You have not been charged; please try again shortly.");
    }

    private void assertOwner(Payment payment, String customerId) {
        if (!payment.getCustomerId().equals(customerId)) {
            throw ApiException.notFound("PAYMENT_NOT_FOUND", "Payment not found.");
        }
    }

    Instant now() {
        return clock.instant();
    }
}
