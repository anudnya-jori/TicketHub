package com.tickethub.payment.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The relay (Scenario 3): publishes unsent outbox rows, oldest first, with the BOOKING ID AS KEY (Scenario 6: one
 * booking's events share a partition, so they stay in order), then marks each one published.
 * Delivery is AT-LEAST-ONCE: if the app dies between the send and the mark, the row is sent again, which is why every
 * consumer is idempotent (inbox). On the first failure the batch stops so later events never overtake earlier ones.
 * Kafka down => rows simply wait; the payment itself was already committed.
 */
@Component
@ConditionalOnProperty(name = "payment.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final long sendTimeoutMs;

    public OutboxRelay(OutboxEventRepository repository, KafkaTemplate<String, String> kafkaTemplate,
                       TransactionTemplate tx, Clock clock,
                       @Value("${payment.outbox.send-timeout-ms:5000}") long sendTimeoutMs) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.tx = tx;
        this.clock = clock;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    @Scheduled(fixedDelayString = "${payment.outbox.relay-interval-ms:1000}", initialDelayString = "${payment.outbox.initial-delay-ms:3000}")
    public void relay() {
        int sent = publishPending();
        if (sent > 0) {
            log.info("OUTBOX_RELAYED {} event(s)", sent);
        }
    }

    /** Returns how many rows were published in this run. Public so tests and operators can trigger a run. */
    public int publishPending() {
        List<OutboxEvent> batch = repository.findTop100ByPublishedAtIsNullOrderByIdAsc();
        int sent = 0;
        for (OutboxEvent e : batch) {
            try {
                kafkaTemplate.send(e.getTopic(), String.valueOf(e.getAggregateId()), e.getPayload())
                        .get(sendTimeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return sent;
            } catch (Exception ex) {
                log.warn("OUTBOX_PUBLISH_FAILED eventId={} topic={} (will retry): {}", e.getEventId(), e.getTopic(), ex.toString());
                return sent;
            }
            tx.executeWithoutResult(s -> repository.markPublished(e.getId(), clock.instant()));
            sent++;
        }
        return sent;
    }
}
