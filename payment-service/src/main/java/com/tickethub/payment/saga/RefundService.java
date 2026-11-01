package com.tickethub.payment.saga;

import com.tickethub.payment.domain.Payment;
import com.tickethub.payment.event.BookingEventMessage;
import com.tickethub.payment.event.InvalidEventException;
import com.tickethub.payment.event.PaymentEvent;
import com.tickethub.payment.inbox.InboxEventRepository;
import com.tickethub.payment.provider.PaymentProviderGateway;
import com.tickethub.payment.provider.RefundOutcome;
import com.tickethub.payment.repository.PaymentRepository;
import com.tickethub.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import com.tickethub.payment.provider.ProviderIds;

/**
 * The compensation (Scenario 5): Booking says "I cannot keep this booking" (BookingRejected: payment arrived after
 * expiry/cancellation; BookingCancelled: the customer cancelled a paid booking) and Payment gives the money back.
 *
 * Exactly-one-refund rules, in layers:
 *  1. inbox: the same event id is processed once;
 *  2. the payment row is locked (SELECT ... FOR UPDATE) and its state machine allows SUCCESS -> REFUNDED only once;
 *  3. the provider refund id is derived from the payment reference, so even if the database step fails after the
 *     provider refunded, the retry gets the SAME refund back instead of creating another;
 *  4. a unique index on payments.refund_id.
 * Everything runs in one transaction: if the provider call fails, the inbox row rolls back too and Kafka retries the
 * event (bounded retries, then the dead-letter topic). A held transaction during one bounded provider call is the
 * deliberate trade for never losing a refund request.
 */
@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final PaymentRepository paymentRepository;
    private final InboxEventRepository inbox;
    private final PaymentProviderGateway gateway;
    private final PaymentService paymentService;
    private final TransactionTemplate tx;
    private final Clock clock;

    public RefundService(PaymentRepository paymentRepository, InboxEventRepository inbox,
                         PaymentProviderGateway gateway, PaymentService paymentService, TransactionTemplate tx,
                         Clock clock) {
        this.paymentRepository = paymentRepository;
        this.inbox = inbox;
        this.gateway = gateway;
        this.paymentService = paymentService;
        this.tx = tx;
        this.clock = clock;
    }

    /** Returns what happened: DUPLICATE | STALE | NO_PAYMENT | NOOP | IGNORED | REFUNDED (useful for tests and logs). */
    public String handleBookingEvent(BookingEventMessage event) {
        if (event == null || event.eventId() == null || event.eventId().isBlank()
                || event.eventType() == null || event.bookingId() == null) {
            throw new InvalidEventException("Booking event needs eventId, eventType and bookingId: " + event);
        }
        MDC.put("bookingId", String.valueOf(event.bookingId()));
        try {
            String outcome = tx.execute(status -> apply(event));
            log.info("BOOKING_EVENT eventId={} type={} v={} -> {}", event.eventId(), event.eventType(),
                    event.aggregateVersionOrZero(), outcome);
            return outcome;
        } finally {
            MDC.remove("bookingId");
        }
    }

    private String apply(BookingEventMessage event) {
        Instant now = clock.instant();
        if (inbox.insertIfAbsent(event.eventId(), event.eventType(), event.bookingId(), now) == 0) {
            return "DUPLICATE";
        }
        boolean refundTrigger = BookingEventMessage.REJECTED.equals(event.eventType())
                || BookingEventMessage.CANCELLED.equals(event.eventType());
        if (!refundTrigger) {
            return "IGNORED";                       // unknown type: forward-compatible
        }
        Payment payment = paymentRepository.findByBookingIdForUpdate(event.bookingId()).orElse(null);
        if (payment == null) {
            log.warn("REFUND_SKIPPED no payment exists for booking {}", event.bookingId());
            return "NO_PAYMENT";
        }
        long version = event.aggregateVersionOrZero();
        if (version < payment.getLastBookingEventVersion()) {
            return "STALE";
        }
        payment.recordBookingEventVersion(version);
        return switch (payment.getStatus()) {
            case REFUNDED, FAILED -> "NOOP";        // already refunded, or nothing was ever taken
            case INITIATED, PENDING -> throw new IllegalStateException(
                    "Payment " + payment.getId() + " is " + payment.getStatus() + ": cannot refund yet, will retry");
            case SUCCESS -> refund(payment, now, event.reason());
        };
    }

    private String refund(Payment payment, Instant now, String reason) {
        RefundOutcome refund = gateway.refund(new ProviderIds(payment.getProviderReference(),
                payment.getProviderOrderId(), payment.getProviderPaymentId()), payment.getAmount(), payment.getCurrency());
        payment.markRefunded(refund.refundId(), now);
        paymentRepository.save(payment);
        paymentService.enqueue(payment, PaymentEvent.REFUNDED, reason);
        log.info("PAYMENT_REFUNDED paymentId={} bookingId={} refundId={} reason={}", payment.getId(),
                payment.getBookingId(), refund.refundId(), reason);
        return "REFUNDED";
    }
}
