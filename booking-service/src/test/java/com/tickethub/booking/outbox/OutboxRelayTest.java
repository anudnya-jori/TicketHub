package com.tickethub.booking.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** The relay publishes oldest first with the BOOKING ID as key, marks rows published, and stops at the first failure. */
class OutboxRelayTest {

    private OutboxEventRepository repository;
    private KafkaTemplate<String, String> kafka;
    private OutboxRelay relay;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(OutboxEventRepository.class);
        kafka = mock(KafkaTemplate.class);
        TransactionTemplate tx = mock(TransactionTemplate.class);
        doAnswer(inv -> {
            ((Consumer<TransactionStatus>) inv.getArgument(0)).accept(mock(TransactionStatus.class));
            return null;
        }).when(tx).executeWithoutResult(any());
        relay = new OutboxRelay(repository, kafka, tx, Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC), 1000);
    }

    private OutboxEvent row(String eventId, long bookingId) {
        return new OutboxEvent(eventId, "T", "topic.x", bookingId, "{\"id\":\"" + eventId + "\"}", Instant.now());
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishesInOrderWithTheBookingIdAsKeyAndMarksEachRowPublished() {
        when(repository.findTop100ByPublishedAtIsNullOrderByIdAsc()).thenReturn(List.of(row("e1", 7), row("e2", 7)));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.<SendResult<String, String>>completedFuture(null));

        assertEquals(2, relay.publishPending());

        var order = inOrder(kafka);
        order.verify(kafka).send(eq("topic.x"), eq("7"), eq("{\"id\":\"e1\"}"));
        order.verify(kafka).send(eq("topic.x"), eq("7"), eq("{\"id\":\"e2\"}"));
        verify(repository, times(2)).markPublished(any(), any());
    }

    @Test
    void kafkaDownMeansNothingIsMarkedPublishedAndLaterRowsDoNotOvertakeEarlierOnes() {
        when(repository.findTop100ByPublishedAtIsNullOrderByIdAsc()).thenReturn(List.of(row("e1", 7), row("e2", 8)));
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka down")));

        assertEquals(0, relay.publishPending());

        verify(kafka, times(1)).send(anyString(), anyString(), anyString());      // stopped at the first failure
        verify(repository, never()).markPublished(any(), any());                   // the rows stay for the next run
    }

    private static String anyString() {
        return org.mockito.ArgumentMatchers.anyString();
    }
}
