package com.tickethub.notification.service;

import com.tickethub.notification.domain.Notification;
import com.tickethub.notification.domain.NotificationStatus;
import com.tickethub.notification.dto.NotificationResponse;
import com.tickethub.notification.event.BookingConfirmedEvent;
import com.tickethub.notification.event.EmailSendException;
import com.tickethub.notification.event.InvalidEventException;
import com.tickethub.notification.inbox.InboxEventRepository;
import com.tickethub.notification.metrics.NotificationMetrics;
import com.tickethub.notification.repository.NotificationRepository;
import com.tickethub.notification.sender.EmailSender;
import com.tickethub.notification.sender.ProviderGuard;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;

/**
 * Turns a BookingConfirmed event into ONE email (ST-20/ST-37), as an IDEMPOTENT CONSUMER (Phase 2, Scenario 2).
 *
 * <ol>
 *   <li>One short transaction records the event id in the inbox table AND creates the notification row. The inbox
 *       primary key decides which delivery is "first": a second copy (Kafka redelivery, DLT replay, two instances)
 *       inserts nothing.</li>
 *   <li>A second transaction locks the notification row (SELECT ... FOR UPDATE), sends the email and marks it SENT.</li>
 * </ol>
 * Duplicate whose first attempt never finished sending (the consumer died after step 1, or the mail server failed):
 * the notification is still PENDING/FAILED, so the duplicate RESUMES the send instead of dropping it. A notification
 * that is already SENT is never sent again. The row lock makes two instances take turns.
 * Residual risk (inherent to any email sender): a crash AFTER the SMTP server accepted the mail but BEFORE the row
 * is marked SENT can send that one email twice. Kafka cannot remove that window; the inbox keeps it to that case only.
 *
 * A message that is broken (no recipient, ...) throws {@link InvalidEventException}: it is non-retryable and goes to
 * the dead-letter topic (Scenario 1). A failing mail server throws EmailSendException (row RETRYING): the delayed retry
 * topics re-deliver it (Scenario 10); after the last attempt the row is FAILED and the record sits on the DLT.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;
    private final InboxEventRepository inbox;
    private final EmailSender sender;
    private final Clock clock;
    private final NotificationMetrics metrics;
    private final TransactionTemplate tx;
    private final ProviderGuard guard;
    private final boolean replayMode;
    private final long simulatedDelayMs;

    public NotificationService(NotificationRepository repository, InboxEventRepository inbox, EmailSender sender,
                               Clock clock, NotificationMetrics metrics, TransactionTemplate tx, ProviderGuard guard,
                               @Value("${notification.replay-mode:false}") boolean replayMode,
                               @Value("${notification.simulated-delay-ms:0}") long simulatedDelayMs) {
        this.repository = repository;
        this.inbox = inbox;
        this.sender = sender;
        this.clock = clock;
        this.metrics = metrics;
        this.tx = tx;
        this.guard = guard;
        this.replayMode = replayMode;
        this.simulatedDelayMs = simulatedDelayMs;
    }

    public void process(BookingConfirmedEvent event) {
        validate(event);
        MDC.put("bookingId", String.valueOf(event.bookingId()));
        try {
            Long notificationId = claim(event);
            if (notificationId != null) {
                deliver(notificationId);
                return;
            }
            // Duplicate delivery of an event already in the inbox.
            Notification existing = repository.findByEventId(event.eventId()).orElse(null);
            if (existing != null && existing.getStatus() != NotificationStatus.SENT) {
                log.info("NOTIFICATION_RESUME eventId={} status={} (first attempt never finished)",
                        event.eventId(), existing.getStatus());
                deliver(existing.getId());
            } else {
                metrics.notification("DUPLICATE");
                log.info("NOTIFICATION_DUPLICATE eventId={} ignored", event.eventId());
            }
        } finally {
            MDC.remove("bookingId");
        }
    }

    /** Inbox row + notification row in ONE transaction. Returns the new notification id, or null for a duplicate. */
    private Long claim(BookingConfirmedEvent event) {
        return tx.execute(status -> {
            int inserted = inbox.insertIfAbsent(event.eventId(), "BookingConfirmed", event.bookingId(), clock.instant());
            if (inserted == 0) {
                return null;
            }
            Notification n = new Notification(event.eventId(), event.bookingId(), event.recipient(),
                    "Your TicketHub booking #" + event.bookingId() + " is confirmed", body(event), clock.instant());
            return repository.saveAndFlush(n).getId();
        });
    }

    /**
     * Locks the row, sends once, records the outcome. Safe to call again: SENT/REPLAYED rows are skipped.
     * Scenario 12: in replay mode the record is rebuilt but NOTHING is sent.
     * Scenario 10: a provider failure marks the row RETRYING and throws EmailSendException AFTER the transaction commits,
     * so the retry-topic machinery re-delivers the event later; the inbox + SENT check keep it to one email.
     */
    private void deliver(Long notificationId) {
        String failure = tx.execute(status -> {
            Notification n = repository.findByIdForUpdate(notificationId).orElse(null);
            if (n == null || n.getStatus() == NotificationStatus.SENT || n.getStatus() == NotificationStatus.REPLAYED) {
                metrics.notification("DUPLICATE");
                return null;
            }
            if (replayMode) {
                n.markReplayed(clock.instant());
                repository.save(n);
                metrics.notification("REPLAYED");
                return null;
            }
            try {
                if (simulatedDelayMs > 0) {
                    Thread.sleep(simulatedDelayMs);      // Scenario 11: a deliberately slow consumer
                }
                guard.send(sender, n.getRecipient(), n.getSubject(), n.getBody());
                n.markSent(clock.instant());
                metrics.notification("SENT");
                log.info("NOTIFICATION_SENT eventId={} bookingId={}", n.getEventId(), n.getBookingId());
                repository.save(n);
                return null;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return "interrupted";
            } catch (RuntimeException e) {
                n.markRetrying(e.toString(), clock.instant());
                repository.save(n);
                metrics.notification("RETRYING");
                log.error("NOTIFICATION_RETRYING eventId={} bookingId={}: {}", n.getEventId(), n.getBookingId(), e.toString());
                return e.toString();
            }
        });
        if (failure != null) {
            throw new EmailSendException("Email provider failed: " + failure, null);
        }
    }

    /** Called when the last retry failed (dead-letter): the notification is finally FAILED. */
    public void markDeadLettered(String eventId, String reason) {
        tx.executeWithoutResult(s -> repository.findByEventId(eventId).ifPresent(n -> {
            if (n.getStatus() != NotificationStatus.SENT && n.getStatus() != NotificationStatus.REPLAYED) {
                n.markFailed(reason, clock.instant());
                repository.save(n);
            }
        }));
    }

    private void validate(BookingConfirmedEvent event) {
        if (event == null || blank(event.eventId()) || event.bookingId() == null || blank(event.recipient())) {
            log.warn("NOTIFICATION_INVALID event {}", event);
            throw new InvalidEventException("BookingConfirmed needs eventId, bookingId and recipient: " + event);
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    public List<NotificationResponse> findByBooking(Long bookingId) {
        return repository.findByBookingIdOrderByIdAsc(bookingId).stream().map(NotificationResponse::from).toList();
    }

    private String body(BookingConfirmedEvent e) {
        return "Hi,\n\nYour booking #" + e.bookingId() + " is confirmed.\n"
                + "Show: " + e.showId() + "\n"
                + "Seats: " + e.seatIds() + "\n"
                + "Paid: " + e.effectiveAmount() + " " + e.currency() + "\n\nThank you for using TicketHub.";
    }
}
