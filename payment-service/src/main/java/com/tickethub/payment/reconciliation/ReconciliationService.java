package com.tickethub.payment.reconciliation;

import com.tickethub.payment.client.BookingClient;
import com.tickethub.payment.client.BookingDto;
import com.tickethub.payment.domain.Payment;
import com.tickethub.payment.domain.PaymentStatus;
import com.tickethub.payment.event.BookingEventMessage;
import com.tickethub.payment.event.PaymentEvent;
import com.tickethub.payment.exception.ApiException;
import com.tickethub.payment.outbox.OutboxService;
import com.tickethub.payment.provider.PaymentProviderGateway;
import com.tickethub.payment.provider.ProviderIds;
import com.tickethub.payment.repository.PaymentRepository;
import com.tickethub.payment.saga.RefundService;
import com.tickethub.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Scenarios 8 and 9. Provider = source of truth for money.
 *  (9) resolveUnknownPayments: a PENDING payment (provider call timed out, outcome UNKNOWN) is resolved by FETCHING its
 *      status from the provider, then moved forward (SUCCESS/FAILED) with the normal outbox event. Never backwards.
 *  (8) reconcile: compares each payment with its booking and repairs or flags the difference:
 *      - payment SUCCESS, booking PENDING_PAYMENT -> re-publish PaymentSucceeded (new event id, same version) so Booking confirms;
 *      - payment SUCCESS, booking EXPIRED/CANCELLED/PAYMENT_FAILED/REJECTED -> refund (money must not stay taken);
 *      - booking CONFIRMED but payment not SUCCESS/REFUNDED -> FLAGGED for a human (never auto-fixed);
 *      - provider disagrees with our PENDING record -> resolved as in (9).
 * Limitation: it starts from payments, so a CONFIRMED booking that has NO payment row is not seen (needs a Booking list API).
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    public record Finding(Long bookingId, Long paymentId, String problem, String action) {
    }

    public record Report(Instant at, int checked, List<Finding> findings) {
    }

    private final PaymentRepository payments;
    private final BookingClient bookingClient;
    private final PaymentProviderGateway gateway;
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final OutboxService outbox;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final String eventsTopic;
    private final Duration pendingAfter;

    public ReconciliationService(PaymentRepository payments, BookingClient bookingClient, PaymentProviderGateway gateway,
                                 PaymentService paymentService, RefundService refundService, OutboxService outbox,
                                 TransactionTemplate tx, Clock clock,
                                 @Value("${payment.events.topic}") String eventsTopic,
                                 @Value("${payment.reconciliation.pending-after-seconds:30}") long pendingAfterSeconds) {
        this.payments = payments;
        this.bookingClient = bookingClient;
        this.gateway = gateway;
        this.paymentService = paymentService;
        this.refundService = refundService;
        this.outbox = outbox;
        this.tx = tx;
        this.clock = clock;
        this.eventsTopic = eventsTopic;
        this.pendingAfter = Duration.ofSeconds(pendingAfterSeconds);
    }

    /** Scenario 9: resolve timed-out payments by asking the provider. Returns how many were resolved. */
    public int resolveUnknownPayments() {
        int resolved = 0;
        for (Payment p : payments.findByStatusAndUpdatedAtBefore(PaymentStatus.PENDING, clock.instant().minus(pendingAfter))) {
            if (resolve(p)) {
                resolved++;
            }
        }
        return resolved;
    }

    private boolean resolve(Payment p) {
        String remote = gateway.fetchStatus(new ProviderIds(p.getProviderReference(), p.getProviderOrderId(), p.getProviderPaymentId()));
        if ("SUCCESS".equals(remote)) {
            paymentService.recordOutcome(p, PaymentStatus.SUCCESS, null);
            return true;
        }
        if ("FAILED".equals(remote)) {
            paymentService.recordOutcome(p, PaymentStatus.FAILED, "PROVIDER_REPORTED_FAILURE");
            return true;
        }
        return false;                       // still unknown at the provider: try again next run
    }

    public Report reconcile() {
        List<Finding> findings = new ArrayList<>();
        resolveUnknownPayments();
        List<Payment> all = payments.findAll();
        for (Payment p : all) {
            try {
                check(p, findings);
            } catch (ApiException | IllegalStateException e) {
                findings.add(new Finding(p.getBookingId(), p.getId(), "CHECK_FAILED: " + e.getMessage(), "RETRY_NEXT_RUN"));
            }
        }
        findings.forEach(f -> log.warn("RECONCILIATION {}", f));
        return new Report(clock.instant(), all.size(), findings);
    }

    private void check(Payment p, List<Finding> findings) {
        if (p.getStatus() == PaymentStatus.SUCCESS) {
            BookingDto b = bookingClient.getBooking(p.getBookingId());
            switch (b.status()) {
                case "CONFIRMED" -> { }
                case "PENDING_PAYMENT" -> {
                    republishSuccess(p);
                    findings.add(new Finding(p.getBookingId(), p.getId(), "Payment captured, booking still PENDING_PAYMENT",
                            "PaymentSucceeded re-published"));
                }
                default -> {
                    String outcome = refundService.handleBookingEvent(new BookingEventMessage(
                            "recon-refund-" + p.getId(), BookingEventMessage.REJECTED, 1, clock.instant(), p.getBookingId(),
                            p.getLastBookingEventVersion(), p.getCustomerId(), p.getAmount(), p.getCurrency(),
                            "RECONCILIATION_BOOKING_" + b.status()));
                    findings.add(new Finding(p.getBookingId(), p.getId(),
                            "Payment captured but booking is " + b.status(), "Refund: " + outcome));
                }
            }
        } else if (p.getStatus() == PaymentStatus.FAILED || p.getStatus() == PaymentStatus.INITIATED) {
            BookingDto b = bookingClient.getBooking(p.getBookingId());
            if ("CONFIRMED".equals(b.status())) {
                findings.add(new Finding(p.getBookingId(), p.getId(),
                        "Booking CONFIRMED but payment is " + p.getStatus(), "FLAGGED for manual review"));
            }
        }
    }

    private void republishSuccess(Payment p) {
        String eventId = PaymentEvent.idFor(PaymentEvent.SUCCEEDED, p.getId()) + "-recon-" + clock.millis();
        tx.executeWithoutResult(s -> outbox.enqueue(eventsTopic, eventId, PaymentEvent.SUCCEEDED, p.getBookingId(),
                new PaymentEvent(eventId, PaymentEvent.SUCCEEDED, 1, clock.instant(), p.getBookingId(),
                        p.getAggregateVersion(), p.getId(), p.getAmount(), p.getCurrency(), "RECONCILIATION")));
    }
}
