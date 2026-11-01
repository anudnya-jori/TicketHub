package com.tickethub.booking.service;

import com.tickethub.booking.client.EventShowClient;
import com.tickethub.booking.client.ShowDto;
import com.tickethub.booking.domain.Booking;
import com.tickethub.booking.domain.BookingStateMachine;
import com.tickethub.booking.domain.BookingStatus;
import com.tickethub.booking.domain.RefundStatus;
import com.tickethub.booking.dto.BookingResponse;
import com.tickethub.booking.dto.CreateBookingRequest;
import com.tickethub.booking.dto.CreateBookingResult;
import com.tickethub.booking.dto.SagaView;
import com.tickethub.booking.event.BookingConfirmedEvent;
import com.tickethub.booking.event.BookingLifecycleEvent;
import com.tickethub.booking.event.InvalidEventException;
import com.tickethub.booking.event.PaymentEventMessage;
import com.tickethub.booking.exception.ApiException;
import com.tickethub.booking.inbox.InboxEventRepository;
import com.tickethub.booking.outbox.OutboxEventRepository;
import com.tickethub.booking.outbox.OutboxService;
import com.tickethub.booking.metrics.BookingMetrics;
import org.slf4j.MDC;
import com.tickethub.booking.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Booking use cases. Why TransactionTemplate instead of @Transactional on the whole method?
 * createBooking calls Event/Show over HTTP. A database transaction must NOT stay open (holding a connection)
 * while we wait for another service. So: remote calls first, THEN one short transaction for the writes.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private static final String UQ_IDEMPOTENCY = "uq_booking_customer_idempotency";
    private static final String UQ_ACTIVE_SEAT = "uq_booking_seat_active";

    private final BookingRepository bookingRepository;
    private final SeatReservationService seatReservationService;
    private final EventShowClient eventShowClient;
    private final OutboxService outbox;
    private final InboxEventRepository inbox;
    private final InboxEventRepository inboxRepository;
    private final OutboxEventRepository outboxRepository;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final BookingMetrics metrics;
    private final long reservationMinutes;
    private final int maxSeatsPerBooking;
    private final String confirmedTopic;
    private final String lifecycleTopic;

    public BookingService(BookingRepository bookingRepository,
                          SeatReservationService seatReservationService,
                          EventShowClient eventShowClient,
                          OutboxService outbox,
                          InboxEventRepository inbox,
                          OutboxEventRepository outboxRepository,
                          TransactionTemplate tx,
                          Clock clock,
                          BookingMetrics metrics,
                          @Value("${booking.reservation-minutes}") long reservationMinutes,
                          @Value("${booking.max-seats-per-booking}") int maxSeatsPerBooking,
                          @Value("${booking.events.topic}") String confirmedTopic,
                          @Value("${booking.lifecycle-events.topic}") String lifecycleTopic) {
        this.bookingRepository = bookingRepository;
        this.seatReservationService = seatReservationService;
        this.eventShowClient = eventShowClient;
        this.outbox = outbox;
        this.inbox = inbox;
        this.inboxRepository = inbox;
        this.outboxRepository = outboxRepository;
        this.tx = tx;
        this.clock = clock;
        this.metrics = metrics;
        this.reservationMinutes = reservationMinutes;
        this.maxSeatsPerBooking = maxSeatsPerBooking;
        this.confirmedTopic = confirmedTopic;
        this.lifecycleTopic = lifecycleTopic;
    }

    // ------------------------------------------------------------------ create (ST-06, ST-07, ST-08, ST-53)

    public CreateBookingResult createBooking(String customerId, String customerEmail, String idempotencyKey,
                                             CreateBookingRequest request) {
        requireCustomerId(customerId);
        String recipient = customerEmail == null || customerEmail.isBlank() ? customerId + "@tickethub.local" : customerEmail;
        requireIdempotencyKey(idempotencyKey);
        List<Long> seatIds = request.seatIds().stream().sorted().toList();
        validateSeatList(seatIds);
        String fingerprint = fingerprint(request.showId(), seatIds);

        // 1. Retry of an earlier request? Return the ORIGINAL booking (SC-13).
        Optional<Booking> earlier = bookingRepository.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey);
        if (earlier.isPresent()) {
            return replay(earlier.get(), fingerprint);
        }

        // 2. Ask Event/Show (no database transaction is open here).
        ShowDto show = eventShowClient.getShow(request.showId());
        ensureBookable(show);
        ensureSeatsBelongToShow(request.showId(), seatIds);

        // 3. Friendly early check. NOT the guard: two requests can both pass it. The database index is the guard.
        List<Long> alreadyHeld = seatReservationService.findHeldSeatIds(request.showId(), seatIds);
        if (!alreadyHeld.isEmpty()) {
            metrics.seatConflict();
            throw seatUnavailable(alreadyHeld);
        }

        // 4. Price snapshot (ST-46) and expiry.
        BigDecimal amount = show.price().multiply(BigDecimal.valueOf(seatIds.size()));
        Instant now = Instant.now(clock);
        Instant expiryAt = now.plus(Duration.ofMinutes(reservationMinutes));

        // 5. One short transaction: booking row + one RESERVED row per seat. All or nothing.
        try {
            Booking saved = tx.execute(status -> {
                Booking booking = bookingRepository.saveAndFlush(new Booking(customerId, recipient, request.showId(),
                        amount, show.currency(), now, expiryAt, idempotencyKey, fingerprint));
                seatReservationService.reserveSeats(booking, seatIds);
                return booking;
            });
            metrics.created();
            log.info("BOOKING_CREATED bookingId={} customerId={} showId={} seats={} amount={}", saved.getId(),
                    customerId, request.showId(), seatIds, amount);
            return new CreateBookingResult(BookingResponse.from(saved, seatIds), true);
        } catch (DataIntegrityViolationException e) {
            String detail = String.valueOf(e.getMostSpecificCause().getMessage());
            if (detail.contains(UQ_ACTIVE_SEAT)) {
                // Lost the race: someone else's transaction committed this seat first. Ours rolled back completely.
                log.info("Seat conflict for show {} seats {} (customer {})", request.showId(), seatIds, customerId);
                metrics.seatConflict();
                throw seatUnavailable(seatIds);
            }
            if (detail.contains(UQ_IDEMPOTENCY)) {
                // Two identical retries raced each other; the other one won. Return its booking.
                Booking winner = bookingRepository.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey)
                        .orElseThrow(() -> e);
                return replay(winner, fingerprint);
            }
            throw e;
        }
    }

    // ------------------------------------------------------------------ view / cancel (ST-52)

    public BookingResponse getBooking(Long id, String customerId) {
        requireCustomerId(customerId);
        Booking booking = findBooking(id);
        requireOwner(booking, customerId);
        return toResponse(booking);
    }

    /**
     * Cancel (ST-52). A PENDING_PAYMENT booking just frees its seats. A CONFIRMED (paid) booking is a COMPENSATION
     * (Scenario 5): it becomes CANCELLED, the seats go back on sale, refund_status = REQUESTED, and a BookingCancelled
     * event is written to the outbox in the same transaction. Payment refunds; PaymentRefunded later sets REFUNDED.
     */
    public BookingResponse cancelBooking(Long id, String customerId) {
        requireCustomerId(customerId);
        Booking cancelled = tx.execute(status -> {
            Booking booking = lockBooking(id);
            requireOwner(booking, customerId);
            BookingStatus before = booking.getStatus();
            BookingStateMachine.assertTransition(before, BookingStatus.CANCELLED);
            booking.changeStatus(BookingStatus.CANCELLED, Instant.now(clock));
            if (before == BookingStatus.CONFIRMED) {
                seatReservationService.releaseBookedSeats(booking.getId());
                booking.requestRefund();
                enqueueLifecycle(booking, BookingLifecycleEvent.CANCELLED, "CUSTOMER_CANCELLED");
            } else {
                seatReservationService.releaseSeats(booking.getId());
            }
            return booking;
        });
        metrics.cancelled();
        log.info("BOOKING_CANCELLED bookingId={} refund={}", id, cancelled.getRefundStatus());
        return toResponse(cancelled);
    }

    // ------------------------------------------------------------------ expiry (ST-09)

    /** Marks overdue PENDING_PAYMENT bookings EXPIRED and releases their seats. Returns how many were expired. */
    public int expireOverdueBookings() {
        List<Long> overdue = bookingRepository.findIdsByStatusAndExpiryAtBefore(
                BookingStatus.PENDING_PAYMENT, Instant.now(clock));
        int expired = 0;
        for (Long id : overdue) {
            try {
                if (expireOne(id)) {
                    expired++;
                }
            } catch (RuntimeException e) {
                log.error("Could not expire booking {}", id, e);
            }
        }
        return expired;
    }

    private boolean expireOne(Long id) {
        Boolean result = tx.execute(status -> {
            Booking booking = lockBooking(id);   // re-check under lock: it may have been paid/cancelled meanwhile
            if (booking.getStatus() != BookingStatus.PENDING_PAYMENT
                    || booking.getExpiryAt().isAfter(Instant.now(clock))) {
                return false;
            }
            booking.changeStatus(BookingStatus.EXPIRED, Instant.now(clock));
            seatReservationService.releaseSeats(booking.getId());
            return true;
        });
        if (Boolean.TRUE.equals(result)) {
            metrics.expired();
            log.info("BOOKING_EXPIRED bookingId={} (seats released)", id);
        }
        return Boolean.TRUE.equals(result);
    }

    // ------------------------------------------------------------------ saga: payment events (Scenarios 4, 5, 6)

    /**
     * Reacts to PaymentSucceeded / PaymentFailed / PaymentRefunded. Everything happens in ONE transaction:
     *  1. inbox insert (duplicate event id => nothing else happens),
     *  2. lock the booking row,
     *  3. ignore the event if its aggregateVersion is LOWER than one already applied (out-of-order / stale),
     *  4. apply it, writing any follow-up event (BookingConfirmed / BookingRejected) to the outbox.
     * Booking owns booking state: a payment for an expired, cancelled or already-rejected booking is NEVER confirmed;
     * the booking becomes REJECTED and BookingRejected asks Payment for a refund.
     * Returns normally for duplicates, stale and unknown event types (consumed and forgotten). A booking that does not
     * exist, or a broken message, throws InvalidEventException => dead-letter topic.
     */
    public void handlePaymentEvent(PaymentEventMessage event) {
        if (event == null || event.eventId() == null || event.eventId().isBlank()
                || event.eventType() == null || event.bookingId() == null) {
            throw new InvalidEventException("Payment event needs eventId, eventType and bookingId: " + event);
        }
        MDC.put("bookingId", String.valueOf(event.bookingId()));
        try {
            String outcome = tx.execute(status -> applyPaymentEvent(event));
            log.info("PAYMENT_EVENT eventId={} type={} v={} -> {}", event.eventId(), event.eventType(),
                    event.aggregateVersionOrZero(), outcome);
            if ("CONFIRMED".equals(outcome)) {
                metrics.confirmed();
                log.info("BOOKING_CONFIRMED bookingId={}", event.bookingId());
            }
        } finally {
            MDC.remove("bookingId");
        }
    }

    private String applyPaymentEvent(PaymentEventMessage event) {
        Instant now = Instant.now(clock);
        if (inbox.insertIfAbsent(event.eventId(), event.eventType(), event.bookingId(), now) == 0) {
            return "DUPLICATE";
        }
        Booking booking = bookingRepository.findByIdForUpdate(event.bookingId())
                .orElseThrow(() -> new InvalidEventException("Payment event " + event.eventId()
                        + " refers to unknown booking " + event.bookingId()));
        long version = event.aggregateVersionOrZero();
        if (version < booking.getLastPaymentEventVersion()) {
            return "STALE";           // an older fact arriving after a newer one has been applied
        }
        booking.recordPaymentEventVersion(version);
        return switch (event.eventType()) {
            case PaymentEventMessage.SUCCEEDED -> onPaymentSucceeded(booking, now);
            case PaymentEventMessage.FAILED -> onPaymentFailed(booking, now);
            case PaymentEventMessage.REFUNDED -> onPaymentRefunded(booking, now);
            default -> "IGNORED";     // unknown type: forward-compatible
        };
    }

    private String onPaymentSucceeded(Booking booking, Instant now) {
        return switch (booking.getStatus()) {
            case CONFIRMED, REJECTED -> "NOOP";                                   // already handled
            case PENDING_PAYMENT -> {
                if (booking.getExpiryAt().isAfter(now)) {
                    confirm(booking, now);
                    yield "CONFIRMED";
                }
                seatReservationService.releaseSeats(booking.getId());             // overdue, scheduler has not run yet
                yield reject(booking, now, "BOOKING_EXPIRED");
            }
            case EXPIRED -> reject(booking, now, "BOOKING_EXPIRED");
            case CANCELLED -> reject(booking, now, "BOOKING_CANCELLED");
            case PAYMENT_FAILED -> reject(booking, now, "BOOKING_NOT_PAYABLE");
        };
    }

    private String onPaymentFailed(Booking booking, Instant now) {
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            return "NOOP";
        }
        booking.changeStatus(BookingStatus.PAYMENT_FAILED, now);
        seatReservationService.releaseSeats(booking.getId());
        return "PAYMENT_FAILED";
    }

    private String onPaymentRefunded(Booking booking, Instant now) {
        if (booking.getRefundStatus() == RefundStatus.REFUNDED) {
            return "NOOP";
        }
        booking.markRefunded(now);          // final state of a rejected/cancelled booking: money returned
        return "REFUNDED";
    }

    private void confirm(Booking booking, Instant now) {
        booking.changeStatus(BookingStatus.CONFIRMED, now);
        seatReservationService.markBooked(booking.getId());
        List<Long> seatIds = seatReservationService.findSeatIdsByBooking(booking.getId());
        String eventId = BookingConfirmedEvent.idFor(booking.getId());
        outbox.enqueue(confirmedTopic, eventId, BookingConfirmedEvent.TYPE, booking.getId(),
                new BookingConfirmedEvent(eventId, booking.getId(), booking.getCustomerId(), booking.getCustomerEmail(),
                        booking.getShowId(), seatIds, booking.getAmount(), booking.getCurrency(), now,
                        BookingConfirmedEvent.TYPE, BookingConfirmedEvent.VERSION, booking.getAggregateVersion(),
                        booking.getAmount()));
    }

    private String reject(Booking booking, Instant now, String reason) {
        BookingStateMachine.assertTransition(booking.getStatus(), BookingStatus.REJECTED);
        booking.changeStatus(BookingStatus.REJECTED, now);
        booking.requestRefund();
        enqueueLifecycle(booking, BookingLifecycleEvent.REJECTED, reason);
        log.warn("BOOKING_REJECTED bookingId={} reason={} (refund requested)", booking.getId(), reason);
        return "REJECTED";
    }

    private void enqueueLifecycle(Booking booking, String type, String reason) {
        long version = booking.getAggregateVersion();
        String eventId = BookingLifecycleEvent.idFor(type, booking.getId(), version);
        outbox.enqueue(lifecycleTopic, eventId, type, booking.getId(), new BookingLifecycleEvent(eventId, type, 1,
                Instant.now(clock), booking.getId(), version, booking.getCustomerId(), booking.getAmount(),
                booking.getCurrency(), reason));
    }

    /** Scenario 7: "where is booking X, and why?" Answered from the booking row plus its inbox/outbox history. */
    public SagaView explainSaga(Long id) {
        Booking b = findBooking(id);
        String state;
        String why;
        switch (b.getStatus()) {
            case PENDING_PAYMENT -> {
                state = "PAYMENT_PENDING";
                why = "Seats held until " + b.getExpiryAt() + "; waiting for PaymentSucceeded/PaymentFailed.";
            }
            case CONFIRMED -> {
                state = "CONFIRMED";
                why = "PaymentSucceeded was applied; BookingConfirmed is "
                        + (outboxRepository.findByAggregateIdOrderByIdAsc(id).stream()
                        .anyMatch(e -> e.getPublishedAt() == null) ? "waiting in the outbox for Kafka." : "published.");
            }
            case PAYMENT_FAILED, EXPIRED -> {
                state = "FAILED";
                why = "Booking ended as " + b.getStatus() + " without a successful payment; seats were released.";
            }
            default -> {   // CANCELLED, REJECTED
                state = b.getRefundStatus() == RefundStatus.REFUNDED ? "COMPENSATED" : "COMPENSATING";
                why = "Booking is " + b.getStatus() + "; refund is " + b.getRefundStatus() + ".";
            }
        }
        List<SagaView.Step> steps = new java.util.ArrayList<>();
        inboxRepository.findByBookingIdOrderByReceivedAtAsc(id).forEach(e ->
                steps.add(new SagaView.Step("RECEIVED", e.getEventType(), e.getEventId(), e.getReceivedAt())));
        outboxRepository.findByAggregateIdOrderByIdAsc(id).forEach(e ->
                steps.add(new SagaView.Step(e.getPublishedAt() == null ? "QUEUED" : "PUBLISHED", e.getEventType(),
                        e.getEventId(), e.getCreatedAt())));
        steps.sort(java.util.Comparator.comparing(SagaView.Step::at));
        return new SagaView(id, state, b.getStatus().name(), b.getRefundStatus().name(), why, b.getAggregateVersion(), steps);
    }

    /** Service-to-service read (Payment needs amount + status). No owner check: this is not a customer API. */
    public BookingResponse getBookingForService(Long id) {
        return toResponse(findBooking(id));
    }

    // ------------------------------------------------------------------ helpers

    private CreateBookingResult replay(Booking existing, String fingerprint) {
        if (!existing.getRequestFingerprint().equals(fingerprint)) {
            throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED",
                    "This Idempotency-Key was already used for a different request.");
        }
        return new CreateBookingResult(toResponse(existing), false);
    }

    private void ensureBookable(ShowDto show) {
        boolean openAndPublished = "OPEN".equals(show.status()) && "PUBLISHED".equals(show.eventStatus());
        if (!openAndPublished) {
            throw ApiException.conflict("SHOW_NOT_BOOKABLE",
                    "Only OPEN shows of PUBLISHED events can be booked (show is " + show.status()
                            + ", event is " + show.eventStatus() + ").");
        }
        if (!show.startTime().isAfter(Instant.now(clock))) {
            throw ApiException.conflict("SHOW_NOT_BOOKABLE", "This show has already started.");
        }
    }

    private void ensureSeatsBelongToShow(Long showId, List<Long> seatIds) {
        List<Long> layout = eventShowClient.getSeatLayoutIds(showId);
        List<Long> unknown = seatIds.stream().filter(id -> !layout.contains(id)).toList();
        if (!unknown.isEmpty()) {
            throw ApiException.badRequest("INVALID_SEAT",
                    "Seats " + unknown + " do not belong to the venue of show " + showId + ".");
        }
    }

    private void validateSeatList(List<Long> seatIds) {
        if (new HashSet<>(seatIds).size() != seatIds.size()) {
            throw ApiException.badRequest("DUPLICATE_SEATS", "The same seat appears more than once in the request.");
        }
        if (seatIds.size() > maxSeatsPerBooking) {
            throw ApiException.badRequest("TOO_MANY_SEATS",
                    "At most " + maxSeatsPerBooking + " seats can be booked at once.");
        }
    }

    private void requireCustomerId(String customerId) {
        if (customerId == null || customerId.isBlank() || customerId.length() > 64) {
            throw ApiException.badRequest("INVALID_CUSTOMER_ID", "X-Customer-Id must be 1 to 64 characters.");
        }
    }

    private void requireIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 100) {
            throw ApiException.badRequest("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must be 1 to 100 characters.");
        }
    }

    /** Unknown booking and someone else's booking look the same (404), so ids cannot be probed. */
    private void requireOwner(Booking booking, String customerId) {
        if (!booking.getCustomerId().equals(customerId)) {
            throw ApiException.notFound("BOOKING_NOT_FOUND", "Booking " + booking.getId() + " not found.");
        }
    }

    private Booking findBooking(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("BOOKING_NOT_FOUND", "Booking " + id + " not found."));
    }

    /** SELECT ... FOR UPDATE: cancel, expiry and payment result take turns on one booking. */
    private Booking lockBooking(Long id) {
        return bookingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("BOOKING_NOT_FOUND", "Booking " + id + " not found."));
    }

    private BookingResponse toResponse(Booking booking) {
        return BookingResponse.from(booking, seatReservationService.findSeatIdsByBooking(booking.getId()));
    }

    private static ApiException seatUnavailable(List<Long> seatIds) {
        return ApiException.conflict("SEAT_UNAVAILABLE",
                "Seat(s) " + seatIds.stream().map(String::valueOf).collect(Collectors.joining(", "))
                        + " are not available for this show.");
    }

    private static String fingerprint(Long showId, List<Long> sortedSeatIds) {
        return showId + ":" + sortedSeatIds.stream().map(String::valueOf).collect(Collectors.joining(","));
    }
}
