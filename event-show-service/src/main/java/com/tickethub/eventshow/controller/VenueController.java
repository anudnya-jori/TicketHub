package com.tickethub.eventshow.controller;

import com.tickethub.eventshow.dto.AddSeatsRequest;
import com.tickethub.eventshow.dto.SeatResponse;
import com.tickethub.eventshow.dto.VenueRequest;
import com.tickethub.eventshow.dto.VenueResponse;
import com.tickethub.eventshow.service.VenueService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/venues")
public class VenueController {

    private final VenueService venueService;

    public VenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VenueResponse create(@Valid @RequestBody VenueRequest request) {
        return venueService.createVenue(request);
    }

    @PutMapping("/{id}")
    public VenueResponse update(@PathVariable Long id, @Valid @RequestBody VenueRequest request) {
        return venueService.updateVenue(id, request);
    }

    @GetMapping("/{id}")
    public VenueResponse get(@PathVariable Long id) {
        return venueService.getVenue(id);
    }

    /** Admin: define the physical seat layout of a venue. */
    @PostMapping("/{id}/seats")
    @ResponseStatus(HttpStatus.CREATED)
    public List<SeatResponse> addSeats(@PathVariable Long id, @Valid @RequestBody AddSeatsRequest request) {
        return venueService.addSeats(id, request);
    }

    @GetMapping("/{id}/seats")
    public List<SeatResponse> seats(@PathVariable Long id) {
        return venueService.getSeats(id);
    }
}
