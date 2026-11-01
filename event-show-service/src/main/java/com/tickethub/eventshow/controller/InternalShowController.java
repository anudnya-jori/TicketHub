package com.tickethub.eventshow.controller;

import com.tickethub.eventshow.dto.SeatResponse;
import com.tickethub.eventshow.dto.ShowResponse;
import com.tickethub.eventshow.service.ShowService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Service-to-service endpoints (called by Booking). NOT for customers.
 * They are protected by the shared service key (InternalApiKeyFilter), not by a customer token.
 */
@RestController
@RequestMapping("/internal/shows")
public class InternalShowController {

    private final ShowService showService;

    public InternalShowController(ShowService showService) {
        this.showService = showService;
    }

    /** Booking asks for a show's price, status and times (service identity, no customer token). */
    @GetMapping("/{showId}")
    public ShowResponse show(@PathVariable Long showId) {
        return showService.getShow(showId);
    }

    @GetMapping("/{showId}/seat-layout")
    public List<SeatResponse> seatLayout(@PathVariable Long showId) {
        return showService.getSeatLayout(showId);
    }
}
