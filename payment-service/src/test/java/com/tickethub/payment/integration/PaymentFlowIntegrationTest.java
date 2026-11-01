package com.tickethub.payment.integration;

import com.tickethub.payment.client.BookingClient;
import com.tickethub.payment.client.BookingDto;
import com.tickethub.payment.domain.Payment;
import com.tickethub.payment.domain.PaymentStatus;
import com.tickethub.payment.dto.CreatePaymentResult;
import com.tickethub.payment.dto.WebhookResponse;
import com.tickethub.payment.event.BookingEventMessage;
import com.tickethub.payment.exception.ApiException;
import com.tickethub.payment.inbox.InboxEventRepository;
import com.tickethub.payment.outbox.OutboxEvent;
import com.tickethub.payment.outbox.OutboxEventRepository;
import com.tickethub.payment.saga.RefundService;
import com.tickethub.payment.provider.SimulationHints;
import com.tickethub.payment.repository.PaymentRepository;
import com.tickethub.payment.repository.WebhookEventRepository;
import com.tickethub.payment.service.PaymentService;
import com.tickethub.payment.service.WebhookService;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Payment rules against a real PostgreSQL; Booking Service is mocked. Skipped without Docker. */
@SpringBootTest(properties = {"payment.provider-timeout-ms=400", "payment.sync.enabled=false",
        "payment.webhook.secret=test-secret", "payment.reconciliation.pending-after-seconds=0"})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PaymentFlowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired PaymentService paymentService;
    @Autowired WebhookService webhookService;
    @Autowired PaymentRepository paymentRepository;
    @Autowired WebhookEventRepository eventRepository;
    @Autowired OutboxEventRepository outboxRepository;
    @Autowired InboxEventRepository inboxRepository;
    @Autowired RefundService refundService;
    @Autowired com.tickethub.payment.reconciliation.ReconciliationService reconciliation;
    @Autowired MockMvc mockMvc;
    @MockitoBean BookingClient bookingClient;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        inboxRepository.deleteAll();
        eventRepository.deleteAll();
        paymentRepository.deleteAll();
        reset(bookingClient);
        resilient.breaker("payment-provider").reset();   // tests must not inherit an open breaker
        stubBooking(1L, "cust-1", "PENDING_PAYMENT", Instant.now().plusSeconds(600));
    }

    private void stubBooking(Long id, String customer, String status, Instant expiry) {
        when(bookingClient.getBooking(id)).thenReturn(
                new BookingDto(id, customer, new BigDecimal("500.00"), "INR", status, expiry));
    }

    private String code(Runnable r) {
        return assertThrows(ApiException.class, r::run).getCode();
    }

    private List<String> outboxTypes(Long bookingId) {
        return outboxRepository.findByAggregateIdOrderByIdAsc(bookingId).stream().map(OutboxEvent::getEventType).toList();
    }

    private BookingEventMessage bookingEvent(String eventId, String type, Long bookingId, long version) {
        return new BookingEventMessage(eventId, type, 1, Instant.now(), bookingId, version, "cust-1",
                new BigDecimal("500.00"), "INR", "TEST");
    }

    private String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private String webhookBody(String eventId, String ref, String result) {
        return "{\"eventId\":\"" + eventId + "\",\"providerReference\":\"" + ref + "\",\"result\":\"" + result + "\"}";
    }

    @Test
    void statusEndpointAndStandardErrorFormat() throws Exception {
        mockMvc.perform(get("/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("payment-service"));
        mockMvc.perform(get("/does-not-exist")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void successfulPaymentUsesTheBookingAmountAndPublishesPaymentSucceededThroughTheOutbox() {
        CreatePaymentResult r = paymentService.pay("cust-1", 1L, SimulationHints.none());

        assertTrue(r.created());
        assertEquals(PaymentStatus.SUCCESS, r.payment().status());
        assertEquals(new BigDecimal("500.00"), r.payment().amount());
        List<OutboxEvent> outbox = outboxRepository.findByAggregateIdOrderByIdAsc(1L);
        assertEquals(List.of("PaymentSucceeded"), outbox.stream().map(OutboxEvent::getEventType).toList());
        assertEquals("payment.events", outbox.get(0).getTopic());
        assertTrue(outbox.get(0).getPayload().contains("\"bookingId\":1"));
        verify(bookingClient, never()).getBooking(eq(2L));   // and Payment never CALLS Booking to report a result
    }

    @Test
    void declinedPaymentIsFailedAndPaymentFailedIsPublished() {
        CreatePaymentResult r = paymentService.pay("cust-1", 1L, new SimulationHints("FAILED", null));

        assertEquals(PaymentStatus.FAILED, r.payment().status());
        assertEquals("CARD_DECLINED", r.payment().failureReason());
        assertEquals(List.of("PaymentFailed"), outboxTypes(1L));
    }

    @Test
    void payingTwiceReturnsTheSamePaymentAndChargesOnce() {
        CreatePaymentResult first = paymentService.pay("cust-1", 1L, SimulationHints.none());
        CreatePaymentResult second = paymentService.pay("cust-1", 1L, SimulationHints.none());

        assertFalse(second.created());
        assertEquals(first.payment().paymentId(), second.payment().paymentId());
        assertEquals(1, paymentRepository.count());
        assertEquals(List.of("PaymentSucceeded"), outboxTypes(1L));   // one charge, one event
    }

    @Test
    void concurrentPayRequestsForOneBookingCreateExactlyOnePayment() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch go = new CountDownLatch(1);
        java.util.List<Future<Long>> results = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return paymentService.pay("cust-1", 1L, SimulationHints.none()).payment().paymentId();
            }));
        }
        go.countDown();
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (Future<Long> f : results) {
            ids.add(f.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();
        assertEquals(1, ids.size());
        assertEquals(1, paymentRepository.count());
    }

    @Test
    void bookingRulesAreChecked() {
        assertEquals("BOOKING_NOT_FOUND", code(() -> paymentService.pay("someone-else", 1L, SimulationHints.none())));

        stubBooking(1L, "cust-1", "CONFIRMED", Instant.now().plusSeconds(600));
        assertEquals("BOOKING_NOT_PAYABLE", code(() -> paymentService.pay("cust-1", 1L, SimulationHints.none())));

        stubBooking(1L, "cust-1", "PENDING_PAYMENT", Instant.now().minusSeconds(5));
        assertEquals("BOOKING_EXPIRED", code(() -> paymentService.pay("cust-1", 1L, SimulationHints.none())));
        assertEquals(0, paymentRepository.count());
    }

    @Test
    void theResultIsKeptInTheOutboxWhileBookingOrKafkaIsDown() {
        // Phase 2: no REST callback exists any more. The relay is disabled in tests, i.e. "Kafka is down".
        CreatePaymentResult r = paymentService.pay("cust-1", 1L, SimulationHints.none());

        assertEquals(PaymentStatus.SUCCESS, r.payment().status());           // customer still gets the answer
        List<OutboxEvent> waiting = outboxRepository.findByAggregateIdOrderByIdAsc(1L);
        assertEquals(1, waiting.size());
        assertNull(waiting.get(0).getPublishedAt(), "the event waits for the relay; nothing is lost");
            }

    @Test
    void providerTimeoutLeavesPaymentPendingAndReturns504() {
        ApiException ex = assertThrows(ApiException.class,
                () -> paymentService.pay("cust-1", 1L, new SimulationHints(null, 3000L)));

        assertEquals(504, ex.getStatus().value());
        Payment p = paymentRepository.findByBookingId(1L).orElseThrow();
        assertEquals(PaymentStatus.PENDING, p.getStatus());
        assertTrue(outboxTypes(1L).isEmpty(), "a timed-out payment is PENDING: no event until the outcome is known");
        // retry does not create a second payment
        assertFalse(paymentService.pay("cust-1", 1L, SimulationHints.none()).created());
    }

    @Test
    void webhookResolvesAPendingPayment() throws Exception {
        assertThrows(ApiException.class, () -> paymentService.pay("cust-1", 1L, new SimulationHints(null, 3000L)));
        String ref = paymentRepository.findByBookingId(1L).orElseThrow().getProviderReference();
        String body = webhookBody("evt-1", ref, "SUCCESS");

        assertEquals("APPLIED", webhookService.handle(body, sign(body)).result());

        assertEquals(PaymentStatus.SUCCESS, paymentRepository.findByBookingId(1L).orElseThrow().getStatus());
        assertEquals(List.of("PaymentSucceeded"), outboxTypes(1L));
    }

    @Test
    void webhookWithBadSignatureIsRejected() {
        String body = webhookBody("evt-1", "whatever", "SUCCESS");
        assertEquals("INVALID_SIGNATURE", code(() -> webhookService.handle(body, "deadbeef")));
        assertEquals("INVALID_SIGNATURE", code(() -> webhookService.handle(body, null)));
    }

    @Test
    void replayedWebhookIsIgnoredAndLateConflictingWebhookCannotChangeAFinalPayment() throws Exception {
        paymentService.pay("cust-1", 1L, SimulationHints.none());               // SUCCESS
        String ref = paymentRepository.findByBookingId(1L).orElseThrow().getProviderReference();

        String conflicting = webhookBody("evt-9", ref, "FAILED");
        assertEquals("IGNORED", webhookService.handle(conflicting, sign(conflicting)).result());
        WebhookResponse replay = webhookService.handle(conflicting, sign(conflicting));
        assertEquals("DUPLICATE", replay.result());

        assertEquals(PaymentStatus.SUCCESS, paymentRepository.findByBookingId(1L).orElseThrow().getStatus());
    }

    @Test
    void customerSeesOnlyTheirOwnPayment() {
        Long id = paymentService.pay("cust-1", 1L, SimulationHints.none()).payment().paymentId();

        assertEquals(id, paymentService.getPayment(id, "cust-1").paymentId());
        assertEquals("PAYMENT_NOT_FOUND", code(() -> paymentService.getPayment(id, "cust-2")));
    }

    @Autowired com.tickethub.payment.resilience.ResilientCaller resilient;

    @Test
    void repeatedProviderTimeoutsOpenTheCircuitAndNewPaymentsFailFastWith503() {
        // each timed-out attempt is a failure for the breaker (window 10 / min 5 calls / 50%)
        for (long booking = 100; booking < 105; booking++) {
            final long id = booking;
            stubBooking(id, "cust-1", "PENDING_PAYMENT", Instant.now().plusSeconds(600));
            assertEquals(504, assertThrows(ApiException.class,
                    () -> paymentService.pay("cust-1", id, new SimulationHints(null, 3000L))).getStatus().value());
        }
        stubBooking(200L, "cust-1", "PENDING_PAYMENT", Instant.now().plusSeconds(600));

        ApiException ex = assertThrows(ApiException.class,
                () -> paymentService.pay("cust-1", 200L, SimulationHints.none()));

        assertEquals(503, ex.getStatus().value());
        assertEquals("PAYMENT_PROVIDER_UNAVAILABLE", ex.getCode());
        assertTrue(paymentRepository.findByBookingId(200L).isEmpty(), "nothing is recorded when we fail fast");
    }

    // ---------------------------------------------------------------- Scenario 5: compensation

    @Test
    void aRejectedBookingGetsItsMoneyBackAndPaymentRefundedIsPublished() {
        paymentService.pay("cust-1", 1L, SimulationHints.none());
        String ref = paymentRepository.findByBookingId(1L).orElseThrow().getProviderReference();

        String outcome = refundService.handleBookingEvent(bookingEvent("rej-1", BookingEventMessage.REJECTED, 1L, 1));

        assertEquals("REFUNDED", outcome);
        Payment p = paymentRepository.findByBookingId(1L).orElseThrow();
        assertEquals(PaymentStatus.REFUNDED, p.getStatus());
        assertEquals("rfnd_" + ref, p.getRefundId());
        assertEquals(List.of("PaymentSucceeded", "PaymentRefunded"), outboxTypes(1L));
    }

    @Test
    void aPaymentIsNeverRefundedTwice() {
        paymentService.pay("cust-1", 1L, SimulationHints.none());

        assertEquals("REFUNDED", refundService.handleBookingEvent(bookingEvent("rej-1", BookingEventMessage.REJECTED, 1L, 1)));
        assertEquals("DUPLICATE", refundService.handleBookingEvent(bookingEvent("rej-1", BookingEventMessage.REJECTED, 1L, 1)));
        assertEquals("NOOP", refundService.handleBookingEvent(bookingEvent("can-1", BookingEventMessage.CANCELLED, 1L, 2)));

        assertEquals(List.of("PaymentSucceeded", "PaymentRefunded"), outboxTypes(1L));
    }

    @Test
    void parallelRefundRequestsForOnePaymentRefundExactlyOnce() throws Exception {
        paymentService.pay("cust-1", 1L, SimulationHints.none());
        ExecutorService pool = Executors.newFixedThreadPool(5);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> results = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            final String eventId = "rej-par-" + i;                 // five DIFFERENT events about the same payment
            results.add(pool.submit(() -> {
                go.await();
                return refundService.handleBookingEvent(bookingEvent(eventId, BookingEventMessage.REJECTED, 1L, 1));
            }));
        }
        go.countDown();
        long refunded = 0;
        for (Future<String> f : results) {
            if ("REFUNDED".equals(f.get(30, TimeUnit.SECONDS))) {
                refunded++;
            }
        }
        pool.shutdown();
        assertEquals(1, refunded);
        assertEquals(List.of("PaymentSucceeded", "PaymentRefunded"), outboxTypes(1L));
    }

    @Test
    void nothingIsRefundedForAFailedPaymentOrAMissingOne() {
        paymentService.pay("cust-1", 1L, new SimulationHints("FAILED", null));

        assertEquals("NOOP", refundService.handleBookingEvent(bookingEvent("rej-1", BookingEventMessage.REJECTED, 1L, 1)));
        assertEquals("NO_PAYMENT", refundService.handleBookingEvent(bookingEvent("rej-2", BookingEventMessage.REJECTED, 77L, 1)));
        assertEquals(List.of("PaymentFailed"), outboxTypes(1L));
    }

    @Test
    void aStaleBookingEventIsIgnored() {
        paymentService.pay("cust-1", 1L, SimulationHints.none());
        // payment already saw version 5 from Booking and has nothing to refund for it ...
        paymentRepository.findByBookingId(1L).ifPresent(p -> {
            p.recordBookingEventVersion(5);
            paymentRepository.save(p);
        });

        assertEquals("STALE", refundService.handleBookingEvent(bookingEvent("old-1", BookingEventMessage.CANCELLED, 1L, 2)));

        assertEquals(PaymentStatus.SUCCESS, paymentRepository.findByBookingId(1L).orElseThrow().getStatus());
    }

    @Test
    void aBrokenBookingEventIsANonRetryableError() {
        assertThrows(com.tickethub.payment.event.InvalidEventException.class, () -> refundService.handleBookingEvent(
                new BookingEventMessage(null, BookingEventMessage.REJECTED, 1, null, 1L, 1L, null, null, null, null)));
    }

    // ---------------------------------------------------------------- Scenarios 8 and 9
    @Test
    void aTimedOutPaymentIsResolvedByAskingTheProvider() {
        assertThrows(ApiException.class, () -> paymentService.pay("cust-1", 1L, new SimulationHints(null, 3000L)));
        assertEquals(PaymentStatus.PENDING, paymentRepository.findByBookingId(1L).orElseThrow().getStatus());

        assertEquals(1, reconciliation.resolveUnknownPayments());

        assertEquals(PaymentStatus.SUCCESS, paymentRepository.findByBookingId(1L).orElseThrow().getStatus());
        assertEquals(List.of("PaymentSucceeded"), outboxTypes(1L));
    }

    @Test
    void reconciliationRefundsACapturedPaymentWhoseBookingIsDead() {
        paymentService.pay("cust-1", 1L, SimulationHints.none());
        stubBooking(1L, "cust-1", "EXPIRED", Instant.now().minusSeconds(60));

        var report = reconciliation.reconcile();

        assertEquals(1, report.findings().size());
        assertEquals(PaymentStatus.REFUNDED, paymentRepository.findByBookingId(1L).orElseThrow().getStatus());
    }

    @Test
    void reconciliationRepublishesPaymentSucceededWhenBookingNeverConfirmed() {
        paymentService.pay("cust-1", 1L, SimulationHints.none());          // booking stub still PENDING_PAYMENT

        reconciliation.reconcile();

        assertEquals(List.of("PaymentSucceeded", "PaymentSucceeded"), outboxTypes(1L));
    }

    @Test
    void reconciliationFlagsAConfirmedBookingWhosePaymentFailed() {
        paymentService.pay("cust-1", 1L, new SimulationHints("FAILED", null));
        stubBooking(1L, "cust-1", "CONFIRMED", Instant.now().plusSeconds(600));

        var report = reconciliation.reconcile();

        assertTrue(report.findings().get(0).action().startsWith("FLAGGED"));
    }
}
