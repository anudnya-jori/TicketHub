package com.tickethub.booking.controller;

import com.tickethub.booking.dto.BookingResponse;
import com.tickethub.booking.dto.SeatStateResponse;
import com.tickethub.booking.service.BookingService;
import com.tickethub.booking.service.SeatReservationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Service-to-service API. NOT routed by the API Gateway, so customers cannot reach it.
 * (Still unauthenticated between services until sprint S7.)
 */
@RestController
@RequestMapping("/internal")
public class InternalBookingController {

    private final BookingService bookingService;
    private final SeatReservationService seatReservationService;

    public InternalBookingController(BookingService bookingService, SeatReservationService seatReservationService) {
        this.bookingService = bookingService;
        this.seatReservationService = seatReservationService;
    }

    /** Event/Show asks which seats of a show are RESERVED or BOOKED (ST-05). */
    @GetMapping("/shows/{showId}/seat-states")
    public List<SeatStateResponse> seatStates(@PathVariable Long showId) {
        return seatReservationService.getSeatStates(showId);
    }

    /** Payment reads the amount and status of a booking before charging. (Results now travel as events, not REST.) */
    @GetMapping("/bookings/{id}")
    public BookingResponse booking(@PathVariable Long id) {
        return bookingService.getBookingForService(id);
    }

    /** Scenario 7: where is this booking in the saga, and why? */
    @GetMapping("/bookings/{id}/saga")
    public com.tickethub.booking.dto.SagaView saga(@PathVariable Long id) {
        return bookingService.explainSaga(id);
    }
}
