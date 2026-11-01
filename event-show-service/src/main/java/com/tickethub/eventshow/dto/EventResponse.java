package com.tickethub.eventshow.dto;

import com.tickethub.eventshow.domain.Event;
import com.tickethub.eventshow.domain.EventStatus;
import com.tickethub.eventshow.domain.EventType;

import java.time.Instant;

public record EventResponse(Long id, String title, String description, EventType type,
                            EventStatus status, String posterRef, Instant createdAt) {

    public static EventResponse from(Event e) {
        return new EventResponse(e.getId(), e.getTitle(), e.getDescription(), e.getType(),
                e.getStatus(), e.getPosterRef(), e.getCreatedAt());
    }
}
