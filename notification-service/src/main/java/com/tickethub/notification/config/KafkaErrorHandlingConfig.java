package com.tickethub.notification.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.tickethub.notification.event.InvalidEventException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Scenario 1: a bad message must not block the consumer.
 *  - Retryable failures (database/mail hiccups) are retried with a GROWING delay, a bounded number of times.
 *  - Non-retryable failures (bad data) skip the retries and go straight to the dead-letter topic.
 *  - After the last retry the record is published to {topic}.DLT (e.g. booking.confirmed.DLT) and the consumer moves on.
 * Spring Boot plugs a CommonErrorHandler bean into the listener container factory automatically.
 */
@Configuration
public class KafkaErrorHandlingConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaErrorHandlingConfig.class);

    public static final String DLT_SUFFIX = ".DLT";

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template,
                                                 @Value("${notification.retry.max-retries:3}") int maxRetries,
                                                 @Value("${notification.retry.initial-interval-ms:1000}") long initialMs,
                                                 @Value("${notification.retry.multiplier:2.0}") double multiplier,
                                                 @Value("${notification.retry.max-interval-ms:10000}") long maxMs) {
        // partition -1 = "let the producer choose": the DLT does not have to mirror the source partitions
        DeadLetterPublishingRecoverer dlt = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + DLT_SUFFIX, -1));
        ConsumerRecordRecoverer logging = (record, ex) -> {
            log.error("DEAD_LETTERED topic={} partition={} offset={} cause={}", record.topic(), record.partition(),
                    record.offset(), rootMessage(ex));
            dlt.accept(record, ex);
        };
        return buildErrorHandler(logging, maxRetries, initialMs, multiplier, maxMs);
    }

    /** Separate from the bean method so a unit test can plug in its own recoverer. */
    public static DefaultErrorHandler buildErrorHandler(ConsumerRecordRecoverer recoverer, int maxRetries,
                                                        long initialMs, double multiplier, long maxMs) {
        ExponentialBackOff backOff = new ExponentialBackOff(initialMs, multiplier);
        backOff.setMaxInterval(maxMs);
        backOff.setMaxAttempts(maxRetries);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        // Bad data can never succeed later: no retries, straight to the dead-letter topic.
        handler.addNotRetryableExceptions(InvalidEventException.class, JsonProcessingException.class,
                IllegalArgumentException.class);
        return handler;
    }

    private static String rootMessage(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null && r.getCause() != r) {
            r = r.getCause();
        }
        return r.toString();
    }

    @Bean
    public NewTopic bookingConfirmedTopic(@Value("${notification.topic}") String topic,
                                          @Value("${notification.topic-partitions:3}") int partitions) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(1)
                .config("retention.ms", "604800000")      // 7 days: how far back a replay can go (Scenario 12)
                .build();
    }

    @Bean
    public NewTopic bookingConfirmedDeadLetterTopic(@Value("${notification.topic}") String topic,
                                                    @Value("${notification.topic-partitions:3}") int partitions) {
        return TopicBuilder.name(topic + DLT_SUFFIX).partitions(partitions).replicas(1).build();
    }
}
