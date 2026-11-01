package com.tickethub.payment.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.tickethub.payment.event.InvalidEventException;
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
 * Topics Payment owns or consumes, plus the consumer error handling (Scenario 1: bounded retries with growing delay,
 * bad data goes straight to {topic}.DLT). Created on startup if missing; 3 partitions so several consumers of one
 * group can share the work (ST-60), and so the bookingId key spreads bookings over partitions (Scenario 6).
 */
@Configuration
public class KafkaTopicConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaTopicConfig.class);
    public static final String DLT_SUFFIX = ".DLT";

    @Bean
    public NewTopic paymentEventsTopic(@Value("${payment.events.topic}") String topic,
                                       @Value("${payment.events.partitions}") int partitions) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(1).build();
    }

    @Bean
    public NewTopic bookingEventsTopic(@Value("${payment.booking-events.topic}") String topic,
                                       @Value("${payment.events.partitions}") int partitions) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(1).build();
    }

    @Bean
    public NewTopic bookingEventsDeadLetterTopic(@Value("${payment.booking-events.topic}") String topic,
                                                 @Value("${payment.events.partitions}") int partitions) {
        return TopicBuilder.name(topic + DLT_SUFFIX).partitions(partitions).replicas(1).build();
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template,
                                                 @Value("${payment.retry.max-retries:3}") int maxRetries,
                                                 @Value("${payment.retry.initial-interval-ms:1000}") long initialMs,
                                                 @Value("${payment.retry.multiplier:2.0}") double multiplier,
                                                 @Value("${payment.retry.max-interval-ms:10000}") long maxMs) {
        DeadLetterPublishingRecoverer dlt = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + DLT_SUFFIX, -1));
        ConsumerRecordRecoverer logging = (record, ex) -> {
            log.error("DEAD_LETTERED topic={} partition={} offset={} cause={}", record.topic(), record.partition(),
                    record.offset(), ex.getMessage());
            dlt.accept(record, ex);
        };
        return buildErrorHandler(logging, maxRetries, initialMs, multiplier, maxMs);
    }

    public static DefaultErrorHandler buildErrorHandler(ConsumerRecordRecoverer recoverer, int maxRetries,
                                                        long initialMs, double multiplier, long maxMs) {
        ExponentialBackOff backOff = new ExponentialBackOff(initialMs, multiplier);
        backOff.setMaxInterval(maxMs);
        backOff.setMaxAttempts(maxRetries);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(InvalidEventException.class, JsonProcessingException.class,
                IllegalArgumentException.class);
        return handler;
    }
}
