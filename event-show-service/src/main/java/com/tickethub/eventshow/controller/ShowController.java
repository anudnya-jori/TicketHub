package com.tickethub.eventshow.controller;

import com.tickethub.eventshow.domain.ShowStatus;
import com.tickethub.eventshow.dto.PageResponse;
import com.tickethub.eventshow.dto.ShowRequest;
import com.tickethub.eventshow.dto.ShowResponse;
import com.tickethub.eventshow.dto.ShowSeatsResponse;
import com.tickethub.eventshow.dto.ShowUpdateRequest;
import com.tickethub.eventshow.service.SeatAvailabilityService;
import com.tickethub.eventshow.service.ShowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/shows")
public class ShowController {

    private final ShowService showService;
    private final SeatAvailabilityService seatAvailabilityService;

    public ShowController(ShowService showService, SeatAvailabilityService seatAvailabilityService) {
        this.showService = showService;
        this.seatAvailabilityService = seatAvailabilityService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShowResponse create(@Valid @RequestBody ShowRequest request) {
        return showService.createShow(request);
    }

    @PutMapping("/{id}")
    public ShowResponse update(@PathVariable Long id, @Valid @RequestBody ShowUpdateRequest request) {
        return showService.updateShow(id, request);
    }

    @PostMapping("/{id}/open")
    public ShowResponse open(@PathVariable Long id) {
        return showService.open(id);
    }

    @PostMapping("/{id}/cancel")
    public ShowResponse cancel(@PathVariable Long id) {
        return showService.cancel(id);
    }

    @GetMapping
    public PageResponse<ShowResponse> list(@RequestParam(required = false) Long eventId,
                                           @RequestParam(required = false) ShowStatus status,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return showService.listShows(eventId, status, page, size);
    }

    @GetMapping("/{id}")
    public ShowResponse get(@PathVariable Long id) {
        return showService.getShow(id);
    }

    /** Seat map for a show: layout from this service merged with reserved/booked seats from Booking. */
    @GetMapping("/{showId}/seats")
    public ShowSeatsResponse seats(@PathVariable Long showId) {
        return seatAvailabilityService.getSeatsForShow(showId);
    }
}
