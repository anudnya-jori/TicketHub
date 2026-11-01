package com.tickethub.eventshow.service;

import com.tickethub.eventshow.client.BookingClient;
import com.tickethub.eventshow.domain.Seat;
import com.tickethub.eventshow.domain.Show;
import com.tickethub.eventshow.domain.SeatState;
import com.tickethub.eventshow.dto.SeatAvailabilityResponse;
import com.tickethub.eventshow.dto.SeatStateDto;
import com.tickethub.eventshow.dto.ShowSeatsResponse;
import com.tickethub.eventshow.repository.SeatRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges two sources of truth into the customer's seat map:
 * layout (this service) + reserved/booked seats (Booking service). AVAILABLE is simply "not held".
 *
 * Deliberately NOT @Transactional: no database transaction should stay open during the REST call.
 */
@Service
public class SeatAvailabilityService {

    private final ShowService showService;
    private final SeatRepository seatRepository;
    private final BookingClient bookingClient;

    public SeatAvailabilityService(ShowService showService, SeatRepository seatRepository,
                                   BookingClient bookingClient) {
        this.showService = showService;
        this.seatRepository = seatRepository;
        this.bookingClient = bookingClient;
    }

    public ShowSeatsResponse getSeatsForShow(Long showId) {
        Show show = showService.findShow(showId);
        List<Seat> layout = seatRepository.findByVenueIdOrderByRowLabelAscSeatNumberAsc(show.getVenueId());

        Map<Long, SeatState> heldSeats = new HashMap<>();
        for (SeatStateDto dto : bookingClient.getSeatStates(showId)) {
            heldSeats.put(dto.seatId(), SeatState.valueOf(dto.status()));
        }

        List<SeatAvailabilityResponse> seats = layout.stream()
                .map(seat -> new SeatAvailabilityResponse(seat.getId(), seat.getRowLabel(), seat.getSeatNumber(),
                        seat.getCategory(), heldSeats.getOrDefault(seat.getId(), SeatState.AVAILABLE)))
                .toList();
        return new ShowSeatsResponse(showId, seats);
    }
}
