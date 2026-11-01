package com.tickethub.eventshow.service;

import com.tickethub.eventshow.domain.Event;
import com.tickethub.eventshow.domain.EventStatus;
import com.tickethub.eventshow.domain.Show;
import com.tickethub.eventshow.domain.ShowStatus;
import com.tickethub.eventshow.domain.Venue;
import com.tickethub.eventshow.dto.PageResponse;
import com.tickethub.eventshow.dto.SeatResponse;
import com.tickethub.eventshow.dto.ShowRequest;
import com.tickethub.eventshow.dto.ShowResponse;
import com.tickethub.eventshow.dto.ShowUpdateRequest;
import com.tickethub.eventshow.exception.ApiException;
import com.tickethub.eventshow.repository.EventRepository;
import com.tickethub.eventshow.repository.SeatRepository;
import com.tickethub.eventshow.repository.ShowRepository;
import com.tickethub.eventshow.repository.VenueRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class ShowService {

    private final ShowRepository showRepository;
    private final EventRepository eventRepository;
    private final VenueRepository venueRepository;
    private final SeatRepository seatRepository;
    private final Clock clock;

    public ShowService(ShowRepository showRepository, EventRepository eventRepository,
                       VenueRepository venueRepository, SeatRepository seatRepository, Clock clock) {
        this.showRepository = showRepository;
        this.eventRepository = eventRepository;
        this.venueRepository = venueRepository;
        this.seatRepository = seatRepository;
        this.clock = clock;
    }

    @Transactional
    public ShowResponse createShow(ShowRequest request) {
        Event event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> ApiException.notFound("EVENT_NOT_FOUND",
                        "Event " + request.eventId() + " not found."));
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw ApiException.conflict("EVENT_CANCELLED", "Cannot create a show for a cancelled event.");
        }
        Venue venue = venueRepository.findById(request.venueId())
                .orElseThrow(() -> ApiException.notFound("VENUE_NOT_FOUND",
                        "Venue " + request.venueId() + " not found."));
        if (!venue.isActive()) {
            throw ApiException.conflict("VENUE_INACTIVE", "Venue " + venue.getId() + " is not active.");
        }
        validateTimes(request.startTime(), request.endTime());

        Show show = new Show(event.getId(), venue.getId(), request.startTime(), request.endTime(),
                request.price(), request.currency(), Instant.now(clock));
        return ShowResponse.from(showRepository.save(show), event.getStatus());
    }

    /**
     * Updating the price does NOT change existing bookings: Booking stores its own price snapshot.
     * Event and venue of a show cannot be changed.
     */
    @Transactional
    public ShowResponse updateShow(Long id, ShowUpdateRequest request) {
        Show show = findShow(id);
        if (show.getStatus() != ShowStatus.SCHEDULED && show.getStatus() != ShowStatus.OPEN) {
            throw ApiException.conflict("INVALID_STATE_TRANSITION",
                    "A show in status " + show.getStatus() + " cannot be updated.");
        }
        validateTimes(request.startTime(), request.endTime());
        show.update(request.startTime(), request.endTime(), request.price(), request.currency(),
                Instant.now(clock));
        return toResponse(show);
    }

    /** SCHEDULED -> OPEN. Only allowed when the event is PUBLISHED. */
    @Transactional
    public ShowResponse open(Long id) {
        Show show = findShow(id);
        Event event = findEvent(show.getEventId());
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw ApiException.conflict("EVENT_NOT_PUBLISHED", "The event must be PUBLISHED before its show can be opened.");
        }
        transition(show, ShowStatus.OPEN);
        return ShowResponse.from(show, event.getStatus());
    }

    @Transactional
    public ShowResponse cancel(Long id) {
        Show show = findShow(id);
        transition(show, ShowStatus.CANCELLED);
        return toResponse(show);
    }

    @Transactional(readOnly = true)
    public ShowResponse getShow(Long id) {
        return toResponse(findShow(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ShowResponse> listShows(Long eventId, ShowStatus status, int page, int size) {
        Specification<Show> spec = (root, query, cb) -> cb.conjunction();
        if (eventId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("eventId"), eventId));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("id"));
        return PageResponse.from(showRepository.findAll(spec, pageable), this::toResponse);
    }

    /** The venue's seat layout for a show. Used internally by Booking to check requested seats belong here. */
    @Transactional(readOnly = true)
    public List<SeatResponse> getSeatLayout(Long showId) {
        Show show = findShow(showId);
        return seatRepository.findByVenueIdOrderByRowLabelAscSeatNumberAsc(show.getVenueId())
                .stream().map(SeatResponse::from).toList();
    }

    public Show findShow(Long id) {
        return showRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("SHOW_NOT_FOUND", "Show " + id + " not found."));
    }

    private Event findEvent(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("EVENT_NOT_FOUND", "Event " + id + " not found."));
    }

    private ShowResponse toResponse(Show show) {
        return ShowResponse.from(show, findEvent(show.getEventId()).getStatus());
    }

    private void validateTimes(Instant start, Instant end) {
        if (!end.isAfter(start)) {
            throw ApiException.badRequest("INVALID_SHOW_TIMES", "endTime must be after startTime.");
        }
        if (!start.isAfter(Instant.now(clock))) {
            throw ApiException.badRequest("INVALID_SHOW_TIMES", "startTime must be in the future.");
        }
    }

    private void transition(Show show, ShowStatus next) {
        if (!show.getStatus().canTransitionTo(next)) {
            throw ApiException.conflict("INVALID_STATE_TRANSITION",
                    "Show cannot go from " + show.getStatus() + " to " + next + ".");
        }
        show.changeStatus(next, Instant.now(clock));
    }
}
