package com.tickethub.payment.saga;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickethub.payment.event.BookingEventMessage;
import com.tickethub.payment.event.InvalidEventException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Booking -> Payment (Scenario 5): BookingRejected / BookingCancelled trigger the refund. */
@Component
public class BookingEventsListener {

    private final ObjectMapper objectMapper;
    private final RefundService refundService;

    public BookingEventsListener(ObjectMapper objectMapper, RefundService refundService) {
        this.objectMapper = objectMapper;
        this.refundService = refundService;
    }

    @KafkaListener(topics = "${payment.booking-events.topic}", groupId = "${payment.booking-events.group-id}")
    public void onMessage(String json) {
        BookingEventMessage event;
        try {
            event = objectMapper.readValue(json, BookingEventMessage.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new InvalidEventException("EVENT_UNREADABLE: " + e.getMessage(), e);
        }
        refundService.handleBookingEvent(event);
    }
}
