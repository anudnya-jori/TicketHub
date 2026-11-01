package com.tickethub.eventshow.controller;

import com.tickethub.eventshow.dto.EventRequest;
import com.tickethub.eventshow.dto.EventResponse;
import com.tickethub.eventshow.dto.PageResponse;
import com.tickethub.eventshow.service.EventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    /** Admin: create an event (starts as DRAFT). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse create(@Valid @RequestBody EventRequest request) {
        return eventService.createEvent(request);
    }

    @PutMapping("/{id}")
    public EventResponse update(@PathVariable Long id, @Valid @RequestBody EventRequest request) {
        return eventService.updateEvent(id, request);
    }

    @PostMapping("/{id}/publish")
    public EventResponse publish(@PathVariable Long id) {
        return eventService.publish(id);
    }

    @PostMapping("/{id}/cancel")
    public EventResponse cancel(@PathVariable Long id) {
        return eventService.cancel(id);
    }

    /** Customer: PUBLISHED events only, paginated. */
    @GetMapping
    public PageResponse<EventResponse> list(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return eventService.listPublished(page, size);
    }

    @GetMapping("/{id}")
    public EventResponse get(@PathVariable Long id) {
        return eventService.getEvent(id);
    }
}
