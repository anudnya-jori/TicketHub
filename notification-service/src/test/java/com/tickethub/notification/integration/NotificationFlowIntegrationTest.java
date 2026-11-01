package com.tickethub.notification.integration;

import com.tickethub.notification.domain.NotificationStatus;
import com.tickethub.notification.event.BookingConfirmedEvent;
import com.tickethub.notification.event.EmailSendException;
import com.tickethub.notification.event.InvalidEventException;
import com.tickethub.notification.inbox.InboxEventRepository;
import com.tickethub.notification.dto.NotificationResponse;
import com.tickethub.notification.listener.BookingConfirmedListener;
import com.tickethub.notification.repository.NotificationRepository;
import com.tickethub.notification.sender.EmailSender;
import com.tickethub.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class NotificationFlowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired NotificationService service;
    @Autowired BookingConfirmedListener listener;
    @Autowired NotificationRepository repository;
    @Autowired InboxEventRepository inboxRepository;
    @Autowired MockMvc mockMvc;
    @MockitoBean EmailSender sender;

    @BeforeEach
    void clean() {
        repository.deleteAll();
        inboxRepository.deleteAll();
        reset(sender);
    }

    private BookingConfirmedEvent event(long bookingId) {
        return new BookingConfirmedEvent("booking-confirmed-" + bookingId, bookingId, "cust-1", "a@b.dev", 10L,
                List.of(1L, 2L), new BigDecimal("500.00"), "INR", Instant.now());
    }

    @Test
    void eventSendsOneEmailAndIsRecordedAsSent() throws Exception {
        service.process(event(1));

        verify(sender, times(1)).send(eq("a@b.dev"), any(), any());
        assertEquals(NotificationStatus.SENT, repository.findByBookingIdOrderByIdAsc(1L).get(0).getStatus());
        mockMvc.perform(get("/internal/notifications").param("bookingId", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("SENT"));
    }

    @Test
    void theSameEventDeliveredTwiceSendsOnlyOneEmail() {
        service.process(event(2));
        service.process(event(2));

        verify(sender, times(1)).send(any(), any(), any());
        assertEquals(1, repository.count());
    }

    @Test
    void aFailingEmailIsRecordedAsRetryingAndThrowsSoTheRetryTopicTakesOver() {
        doThrow(new RuntimeException("smtp down")).when(sender).send(any(), any(), any());

        assertThrows(EmailSendException.class, () -> service.process(event(3)));   // Scenario 10: delayed retry follows

        NotificationResponse n = service.findByBooking(3L).get(0);
        assertEquals(NotificationStatus.RETRYING, n.status());
        service.markDeadLettered("booking-confirmed-3", "retries exhausted");        // after the last attempt
        assertEquals(NotificationStatus.FAILED, service.findByBooking(3L).get(0).status());
    }

    @Test
    void garbageMessagesAreNonRetryableErrorsSoTheyGoToTheDeadLetterTopic() {
        // Phase 2 / Scenario 1: bad data is no longer swallowed. It throws InvalidEventException; the Kafka error
        // handler does not retry that type and parks the record on booking.confirmed.DLT.
        assertThrows(InvalidEventException.class, () -> listener.onMessage("this is not json"));
        assertThrows(InvalidEventException.class, () -> listener.onMessage("{\"eventId\":\"x\"}"));   // no recipient

        verify(sender, never()).send(any(), any(), any());
        assertEquals(0, repository.count());
        assertEquals(0, inboxRepository.count());
    }

    @Test
    void aDuplicateWhoseFirstAttemptFailedResumesTheSendOnce() {
        doThrow(new RuntimeException("smtp down")).doNothing().when(sender).send(any(), any(), any());

        assertThrows(EmailSendException.class, () -> service.process(event(6)));   // mail server down -> RETRYING
        service.process(event(6));                  // retry-topic redelivery / DLT replay: resumes, now succeeds
        service.process(event(6));                  // already SENT: ignored

        verify(sender, times(2)).send(any(), any(), any());
        assertEquals(1, repository.count());
        assertEquals(NotificationStatus.SENT, service.findByBooking(6L).get(0).status());
    }

    @Test
    void fiveReplaysFromTwoConsumerInstancesSendExactlyOneEmail() throws Exception {
        // Two "instances" = two threads racing on the same database (the unique inbox row + row lock decide).
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            futures.add(pool.submit(() -> {
                go.await();
                service.process(event(5));
                return null;
            }));
        }
        go.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        verify(sender, times(1)).send(any(), any(), any());
        assertEquals(1, repository.count());
        assertEquals(1, inboxRepository.count());
    }

    @Test
    void listenerParsesTheBookingContract() {
        listener.onMessage("{\"eventId\":\"booking-confirmed-4\",\"bookingId\":4,\"customerId\":\"c\","
                + "\"recipient\":\"x@y.dev\",\"showId\":1,\"seatIds\":[1],\"amount\":250.00,\"currency\":\"INR\","
                + "\"confirmedAt\":\"2030-01-01T10:00:00Z\",\"someFutureField\":true}");

        verify(sender).send(eq("x@y.dev"), any(), any());
    }

    @Test
    void version1AndVersion2PayloadsBothParse() {
        String v1 = "{\"eventId\":\"booking-confirmed-7\",\"bookingId\":7,\"recipient\":\"a@b.dev\",\"showId\":1,"
                + "\"seatIds\":[1],\"amount\":100.00,\"currency\":\"INR\",\"confirmedAt\":\"2030-01-01T10:00:00Z\"}";
        String v2 = "{\"eventId\":\"booking-confirmed-8\",\"bookingId\":8,\"recipient\":\"a@b.dev\",\"showId\":1,"
                + "\"seatIds\":[1],\"totalAmount\":120.00,\"amount\":120.00,\"currency\":\"INR\","
                + "\"confirmedAt\":\"2030-01-01T10:00:00Z\",\"version\":2,\"aggregateVersion\":3}";
        listener.onMessage(v1);
        listener.onMessage(v2);
        verify(sender, times(2)).send(eq("a@b.dev"), any(), any());
        assertEquals(2, repository.count());
    }
}
