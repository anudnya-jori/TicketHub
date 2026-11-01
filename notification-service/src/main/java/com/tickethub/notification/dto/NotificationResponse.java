package com.tickethub.notification.dto;

import com.tickethub.notification.domain.Notification;
import com.tickethub.notification.domain.NotificationStatus;

import java.time.Instant;

public record NotificationResponse(Long id, String eventId, Long bookingId, String recipient, String subject,
                                   NotificationStatus status, String error, Instant createdAt) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getEventId(), n.getBookingId(), n.getRecipient(),
                n.getSubject(), n.getStatus(), n.getError(), n.getCreatedAt());
    }
}
