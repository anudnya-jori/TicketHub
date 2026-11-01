package com.tickethub.eventshow.service;

import com.tickethub.eventshow.client.BookingClient;
import com.tickethub.eventshow.domain.Seat;
import com.tickethub.eventshow.domain.SeatCategory;
import com.tickethub.eventshow.domain.SeatState;
import com.tickethub.eventshow.domain.Show;
import com.tickethub.eventshow.dto.SeatStateDto;
import com.tickethub.eventshow.dto.ShowSeatsResponse;
import com.tickethub.eventshow.exception.ApiException;
import com.tickethub.eventshow.repository.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeatAvailabilityServiceTest {

    @Mock
    private ShowService showService;
    @Mock
    private SeatRepository seatRepository;
    @Mock
    private BookingClient bookingClient;

    private SeatAvailabilityService service;

    @BeforeEach
    void setUp() {
        service = new SeatAvailabilityService(showService, seatRepository, bookingClient);
        Show show = new Show(5L, 6L, Instant.parse("2030-01-01T10:00:00Z"), Instant.parse("2030-01-01T12:00:00Z"),
                new BigDecimal("250.00"), "INR", Instant.parse("2026-10-23T00:00:00Z"));
        when(showService.findShow(1L)).thenReturn(show);
        when(seatRepository.findByVenueIdOrderByRowLabelAscSeatNumberAsc(6L))
                .thenReturn(List.of(seat(101L, "A", 1), seat(102L, "A", 2), seat(103L, "A", 3)));
    }

    @Test
    void everySeatIsAvailableWhenBookingReportsNoHolds() {
        when(bookingClient.getSeatStates(1L)).thenReturn(List.of());

        ShowSeatsResponse response = service.getSeatsForShow(1L);

        assertEquals(3, response.seats().size());
        assertEquals(List.of(SeatState.AVAILABLE, SeatState.AVAILABLE, SeatState.AVAILABLE),
                response.seats().stream().map(s -> s.status()).toList());
    }

    @Test
    void layoutIsMergedWithReservedAndBookedSeatsFromBooking() {
        when(bookingClient.getSeatStates(1L)).thenReturn(
                List.of(new SeatStateDto(102L, "RESERVED"), new SeatStateDto(103L, "BOOKED")));

        ShowSeatsResponse response = service.getSeatsForShow(1L);

        assertEquals(SeatState.AVAILABLE, response.seats().get(0).status());
        assertEquals(SeatState.RESERVED, response.seats().get(1).status());
        assertEquals(SeatState.BOOKED, response.seats().get(2).status());
    }

    @Test
    void failsClearlyInsteadOfGuessingWhenBookingIsDown() {
        when(bookingClient.getSeatStates(1L)).thenThrow(
                ApiException.unavailable("BOOKING_SERVICE_UNAVAILABLE", "down"));

        ApiException ex = assertThrows(ApiException.class, () -> service.getSeatsForShow(1L));

        assertEquals(503, ex.getStatus().value());
    }

    private Seat seat(Long id, String row, int number) {
        Seat s = new Seat(6L, row, number, SeatCategory.STANDARD);
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }
}
