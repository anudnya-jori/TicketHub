package com.tickethub.eventshow.service;

import com.tickethub.eventshow.domain.Event;
import com.tickethub.eventshow.domain.EventStatus;
import com.tickethub.eventshow.domain.ShowStatus;
import com.tickethub.eventshow.dto.EventRequest;
import com.tickethub.eventshow.dto.EventResponse;
import com.tickethub.eventshow.dto.PageResponse;
import com.tickethub.eventshow.exception.ApiException;
import com.tickethub.eventshow.repository.EventRepository;
import com.tickethub.eventshow.repository.ShowRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final ShowRepository showRepository;
    private final Clock clock;

    public EventService(EventRepository eventRepository, ShowRepository showRepository, Clock clock) {
        this.eventRepository = eventRepository;
        this.showRepository = showRepository;
        this.clock = clock;
    }

    @Transactional
    public EventResponse createEvent(EventRequest request) {
        Event event = new Event(request.title().trim(), request.description(), request.type(),
                request.posterRef(), Instant.now(clock));
        return EventResponse.from(eventRepository.save(event));
    }

    @Transactional
    public EventResponse updateEvent(Long id, EventRequest request) {
        Event event = findEvent(id);
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw ApiException.conflict("EVENT_CANCELLED", "A cancelled event cannot be updated.");
        }
        // a normal update must not wipe an uploaded poster: keep the stored reference unless a new one is given
        String posterRef = request.posterRef() != null ? request.posterRef() : event.getPosterRef();
        event.update(request.title().trim(), request.description(), request.type(), posterRef,
                Instant.now(clock));
        return EventResponse.from(event);
    }

    /** Called by PosterService after the file is safely stored (short transaction, no S3 call inside it). */
    @Transactional
    public EventResponse attachPoster(Long id, String posterKey) {
        Event event = findEvent(id);
        event.changePoster(posterKey, Instant.now(clock));
        return EventResponse.from(event);
    }

    @Transactional
    public EventResponse publish(Long id) {
        Event event = findEvent(id);
        transition(event, EventStatus.PUBLISHED);
        return EventResponse.from(event);
    }

    /** Cancelling an event also cancels all of its shows that are not finished. */
    @Transactional
    public EventResponse cancel(Long id) {
        Event event = findEvent(id);
        transition(event, EventStatus.CANCELLED);
        showRepository.cancelByEventId(id,
                List.of(ShowStatus.SCHEDULED, ShowStatus.OPEN, ShowStatus.SOLD_OUT),
                ShowStatus.CANCELLED, Instant.now(clock));
        return EventResponse.from(event);
    }

    /** Customers only ever see PUBLISHED events in the list. */
    @Transactional(readOnly = true)
    public PageResponse<EventResponse> listPublished(int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("id"));
        return PageResponse.from(eventRepository.findByStatus(EventStatus.PUBLISHED, pageable), EventResponse::from);
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(Long id) {
        return EventResponse.from(findEvent(id));
    }

    private Event findEvent(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("EVENT_NOT_FOUND", "Event " + id + " not found."));
    }

    private void transition(Event event, EventStatus next) {
        if (!event.getStatus().canTransitionTo(next)) {
            throw ApiException.conflict("INVALID_STATE_TRANSITION",
                    "Event cannot go from " + event.getStatus() + " to " + next + ".");
        }
        event.changeStatus(next, Instant.now(clock));
    }
}
