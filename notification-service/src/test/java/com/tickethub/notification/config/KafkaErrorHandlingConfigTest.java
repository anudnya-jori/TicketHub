package com.tickethub.notification.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.tickethub.notification.event.InvalidEventException;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Scenario 1: bad data skips the retries and is dead-lettered on the FIRST failure. */
class KafkaErrorHandlingConfigTest {

    @Test
    @SuppressWarnings("unchecked")
    void invalidEventsAreRecoveredImmediatelyWithoutRetries() {
        List<ConsumerRecord<?, ?>> deadLettered = new ArrayList<>();
        DefaultErrorHandler handler = KafkaErrorHandlingConfig.buildErrorHandler(
                (record, ex) -> deadLettered.add(record), 3, 1000, 2.0, 10000);
        ConsumerRecord<String, String> bad = new ConsumerRecord<>("booking.confirmed", 0, 7L, "1", "{broken");

        boolean recovered = handler.handleOne(
                new ListenerExecutionFailedException("listener failed", new InvalidEventException("EVENT_UNREADABLE")),
                bad, mock(Consumer.class), mock(MessageListenerContainer.class));

        assertTrue(recovered, "non-retryable: the record is given up on at once");
        assertEquals(1, deadLettered.size());
        assertEquals(7L, deadLettered.get(0).offset());
    }

    @Test
    @SuppressWarnings("unchecked")
    void jsonErrorsAreAlsoNonRetryable() {
        List<ConsumerRecord<?, ?>> deadLettered = new ArrayList<>();
        DefaultErrorHandler handler = KafkaErrorHandlingConfig.buildErrorHandler(
                (record, ex) -> deadLettered.add(record), 3, 1000, 2.0, 10000);
        ConsumerRecord<String, String> bad = new ConsumerRecord<>("booking.confirmed", 1, 3L, "1", "{broken");
        JsonProcessingException json = new JsonProcessingException("bad json") { };

        assertTrue(handler.handleOne(new ListenerExecutionFailedException("x", json), bad,
                mock(Consumer.class), mock(MessageListenerContainer.class)));
        assertEquals(1, deadLettered.size());
    }
}
