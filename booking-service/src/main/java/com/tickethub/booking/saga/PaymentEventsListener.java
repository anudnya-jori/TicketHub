package com.tickethub.booking.saga;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickethub.booking.event.InvalidEventException;
import com.tickethub.booking.event.PaymentEventMessage;
import com.tickethub.booking.service.BookingService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Payment -> Booking (Scenario 4): reacts to PaymentSucceeded / PaymentFailed / PaymentRefunded. Nobody calls Booking:
 * while Booking is down the event waits in Kafka and is applied when Booking is back. Unreadable messages are
 * non-retryable and go to payment.events.DLT.
 */
@Component
public class PaymentEventsListener {

    private final ObjectMapper objectMapper;
    private final BookingService bookingService;

    public PaymentEventsListener(ObjectMapper objectMapper, BookingService bookingService) {
        this.objectMapper = objectMapper;
        this.bookingService = bookingService;
    }

    @KafkaListener(topics = "${booking.payment-events.topic}", groupId = "${booking.payment-events.group-id}")
    public void onMessage(String json) {
        PaymentEventMessage event;
        try {
            event = objectMapper.readValue(json, PaymentEventMessage.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new InvalidEventException("EVENT_UNREADABLE: " + e.getMessage(), e);
        }
        bookingService.handlePaymentEvent(event);
    }
}
