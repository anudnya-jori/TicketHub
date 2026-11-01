package com.tickethub.eventshow.service;

import com.tickethub.eventshow.domain.Event;
import com.tickethub.eventshow.domain.EventStatus;
import com.tickethub.eventshow.domain.EventType;
import com.tickethub.eventshow.dto.EventRequest;
import com.tickethub.eventshow.dto.EventResponse;
import com.tickethub.eventshow.exception.ApiException;
import com.tickethub.eventshow.repository.EventRepository;
import com.tickethub.eventshow.repository.ShowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private EventRepository eventRepository;
    @Mock
    private ShowRepository showRepository;

    private EventService eventService;

    @BeforeEach
    void setUp() {
        eventService = new EventService(eventRepository, showRepository, CLOCK);
    }

    @Test
    void newEventStartsAsDraft() {
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        EventResponse response = eventService.createEvent(
                new EventRequest("  Dune  ", "Sci-fi", EventType.MOVIE, null));

        assertEquals(EventStatus.DRAFT, response.status());
        assertEquals("Dune", response.title());
    }

    @Test
    void publishMovesDraftToPublished() {
        when(eventRepository.findById(1L)).thenReturn(Optional.of(draftEvent()));

        EventResponse response = eventService.publish(1L);

        assertEquals(EventStatus.PUBLISHED, response.status());
    }

    @Test
    void publishingTwiceIsRejectedAsInvalidTransition() {
        Event event = draftEvent();
        event.changeStatus(EventStatus.PUBLISHED, Instant.now(CLOCK));
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

        ApiException ex = assertThrows(ApiException.class, () -> eventService.publish(1L));

        assertEquals("INVALID_STATE_TRANSITION", ex.getCode());
        assertEquals(409, ex.getStatus().value());
    }

    @Test
    void draftEventCannotBeCancelledDirectly() {
        when(eventRepository.findById(1L)).thenReturn(Optional.of(draftEvent()));

        ApiException ex = assertThrows(ApiException.class, () -> eventService.cancel(1L));

        assertEquals("INVALID_STATE_TRANSITION", ex.getCode());
    }

    @Test
    void cancellingPublishedEventAlsoCancelsItsShows() {
        Event event = draftEvent();
        event.changeStatus(EventStatus.PUBLISHED, Instant.now(CLOCK));
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

        EventResponse response = eventService.cancel(1L);

        assertEquals(EventStatus.CANCELLED, response.status());
        verify(showRepository).cancelByEventId(any(), anyCollection(), any(), any());
    }

    @Test
    void unknownEventIsNotFound() {
        when(eventRepository.findById(99L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> eventService.getEvent(99L));

        assertEquals("EVENT_NOT_FOUND", ex.getCode());
        assertEquals(404, ex.getStatus().value());
    }

    @Test
    void cancelledEventCannotBeUpdated() {
        Event event = draftEvent();
        event.changeStatus(EventStatus.PUBLISHED, Instant.now(CLOCK));
        event.changeStatus(EventStatus.CANCELLED, Instant.now(CLOCK));
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

        ApiException ex = assertThrows(ApiException.class,
                () -> eventService.updateEvent(1L, new EventRequest("New", null, EventType.MOVIE, null)));

        assertEquals("EVENT_CANCELLED", ex.getCode());
    }

    private Event draftEvent() {
        return new Event("Dune", "Sci-fi", EventType.MOVIE, null, Instant.now(CLOCK));
    }
}
