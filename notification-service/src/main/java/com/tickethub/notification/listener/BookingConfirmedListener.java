package com.tickethub.notification.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickethub.notification.event.BookingConfirmedEvent;
import com.tickethub.notification.event.InvalidEventException;
import com.tickethub.notification.service.NotificationService;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * Kafka -> Notification. An unreadable message throws {@link InvalidEventException}. The container's error handler
 * (see KafkaErrorHandlingConfig) does NOT retry it: it parks it on booking.confirmed.DLT, so the stream moves on
 * (Scenario 1). Before Phase 2 this method swallowed bad data, which hid it; now it is visible and replayable.
 */
@Component
public class BookingConfirmedListener {

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    public BookingConfirmedListener(ObjectMapper objectMapper, NotificationService notificationService) {
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
    }

    /**
     * Scenario 10: NON-BLOCKING retries. A failed email moves to booking.confirmed-retry-0/1/2 with growing delays (the
     * main topic keeps flowing), and after the last attempt to booking.confirmed.DLT. Bad data (InvalidEventException)
     * is excluded from retries and goes straight to the DLT (Scenario 1).
     * groupId is configurable so a NEW group can replay history (Scenario 12).
     */
    @RetryableTopic(attempts = "${notification.retry.topic-attempts:4}", numPartitions = "3",
            backoff = @Backoff(delayExpression = "${notification.retry.topic-delay-ms:5000}", multiplier = 2.0),
            exclude = {InvalidEventException.class},
            dltTopicSuffix = ".DLT", retryTopicSuffix = "-retry", dltStrategy = DltStrategy.FAIL_ON_ERROR)
    @KafkaListener(topics = "${notification.topic}", groupId = "${notification.group-id:notification-service}")
    public void onMessage(String json) {
        BookingConfirmedEvent event;
        try {
            event = objectMapper.readValue(json, BookingConfirmedEvent.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new InvalidEventException("EVENT_UNREADABLE: " + e.getMessage(), e);
        }
        notificationService.process(event);
    }

    /** Runs for every message that ended on the DLT: record the final failure on the notification, if there is one. */
    @DltHandler
    public void onDeadLetter(String json) {
        try {
            BookingConfirmedEvent event = objectMapper.readValue(json, BookingConfirmedEvent.class);
            if (event.eventId() != null) {
                notificationService.markDeadLettered(event.eventId(), "retries exhausted");
            }
        } catch (JsonProcessingException | IllegalArgumentException ignored) {
            // unreadable: nothing to mark, the record stays on the DLT for inspection
        }
    }
}
