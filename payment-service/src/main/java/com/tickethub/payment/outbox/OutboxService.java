package com.tickethub.payment.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Writes an event into the outbox. MANDATORY propagation: it only works INSIDE the transaction that changes the
 * booking, so "payment changed" and "event exists" commit or roll back together. Nothing here talks to Kafka.
 */
@Service
public class OutboxService {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxService(OutboxEventRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent enqueue(String topic, String eventId, String eventType, Long bookingId, Object event) {
        try {
            return repository.save(new OutboxEvent(eventId, eventType, topic, bookingId,
                    objectMapper.writeValueAsString(event), clock.instant()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + eventType + " for booking " + bookingId, e);
        }
    }
}
