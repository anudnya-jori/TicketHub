package com.tickethub.notification.service;

import com.tickethub.notification.domain.Notification;
import com.tickethub.notification.domain.NotificationStatus;
import com.tickethub.notification.event.BookingConfirmedEvent;
import com.tickethub.notification.event.InvalidEventException;
import com.tickethub.notification.inbox.InboxEventRepository;
import com.tickethub.notification.metrics.NotificationMetrics;
import com.tickethub.notification.repository.NotificationRepository;
import com.tickethub.notification.sender.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Idempotent-consumer rules without a database (Scenario 2): the inbox decides, the row lock serialises sending. */
class NotificationServiceTest {

    private NotificationRepository repository;
    private InboxEventRepository inbox;
    private EmailSender sender;
    private NotificationService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(NotificationRepository.class);
        inbox = mock(InboxEventRepository.class);
        sender = mock(EmailSender.class);
        TransactionTemplate tx = mock(TransactionTemplate.class);
        when(tx.execute(any())).thenAnswer(inv -> ((TransactionCallback<Object>) inv.getArgument(0)).doInTransaction(mock(TransactionStatus.class)));
        doAnswer(inv -> {
            ((Consumer<TransactionStatus>) inv.getArgument(0)).accept(mock(TransactionStatus.class));
            return null;
        }).when(tx).executeWithoutResult(any());
        service = new NotificationService(repository, inbox, sender, Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
                mock(NotificationMetrics.class), tx,
                new com.tickethub.notification.sender.ProviderGuard(0, 10, 5, 1000), false, 0);
    }

    private BookingConfirmedEvent event(String recipient) {
        return new BookingConfirmedEvent("booking-confirmed-1", 1L, "c", recipient, 10L, List.of(1L),
                new BigDecimal("250.00"), "INR", Instant.now());
    }

    private Notification notification(boolean sent) {
        Notification n = new Notification("booking-confirmed-1", 1L, "a@b.dev", "s", "b", Instant.now());
        if (sent) {
            n.markSent(Instant.now());
        }
        return n;
    }

    @Test
    void aMessageWithoutRecipientIsNonRetryable() {
        assertThrows(InvalidEventException.class, () -> service.process(event(null)));
        assertThrows(InvalidEventException.class, () -> service.process(event("  ")));
        verifyNoInteractions(inbox, sender);
    }

    @Test
    void aFirstDeliverySavesInboxAndNotificationThenSendsOnce() {
        when(inbox.insertIfAbsent(eq("booking-confirmed-1"), anyString(), eq(1L), any())).thenReturn(1);
        Notification saved = notification(false);
        when(repository.saveAndFlush(any())).thenReturn(saved);
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(saved));

        service.process(event("a@b.dev"));

        verify(sender, times(1)).send(eq("a@b.dev"), any(), any());
        org.junit.jupiter.api.Assertions.assertEquals(NotificationStatus.SENT, saved.getStatus());
    }

    @Test
    void aDuplicateOfASentNotificationSendsNothing() {
        when(inbox.insertIfAbsent(anyString(), anyString(), any(), any())).thenReturn(0);
        when(repository.findByEventId("booking-confirmed-1")).thenReturn(Optional.of(notification(true)));

        service.process(event("a@b.dev"));

        verifyNoInteractions(sender);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void aDuplicateWhoseFirstAttemptNeverFinishedResumesTheSend() {
        when(inbox.insertIfAbsent(anyString(), anyString(), any(), any())).thenReturn(0);
        Notification unfinished = notification(false);          // PENDING: the first consumer died before sending
        when(repository.findByEventId("booking-confirmed-1")).thenReturn(Optional.of(unfinished));
        when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(unfinished));

        service.process(event("a@b.dev"));

        verify(sender, times(1)).send(eq("a@b.dev"), any(), any());
        org.junit.jupiter.api.Assertions.assertEquals(NotificationStatus.SENT, unfinished.getStatus());
    }
}
