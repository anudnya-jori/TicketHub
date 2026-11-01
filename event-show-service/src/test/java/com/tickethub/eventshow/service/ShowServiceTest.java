package com.tickethub.eventshow.service;

import com.tickethub.eventshow.domain.Event;
import com.tickethub.eventshow.domain.EventStatus;
import com.tickethub.eventshow.domain.EventType;
import com.tickethub.eventshow.domain.Show;
import com.tickethub.eventshow.domain.ShowStatus;
import com.tickethub.eventshow.domain.Venue;
import com.tickethub.eventshow.dto.ShowRequest;
import com.tickethub.eventshow.dto.ShowResponse;
import com.tickethub.eventshow.exception.ApiException;
import com.tickethub.eventshow.repository.EventRepository;
import com.tickethub.eventshow.repository.SeatRepository;
import com.tickethub.eventshow.repository.ShowRepository;
import com.tickethub.eventshow.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShowServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-23T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Instant START = NOW.plusSeconds(86_400);
    private static final Instant END = START.plusSeconds(7_200);

    @Mock
    private ShowRepository showRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private VenueRepository venueRepository;
    @Mock
    private SeatRepository seatRepository;

    private ShowService showService;

    @BeforeEach
    void setUp() {
        showService = new ShowService(showRepository, eventRepository, venueRepository, seatRepository, CLOCK);
    }

    @Test
    void newShowStartsScheduledWithPriceAndCurrency() {
        when(eventRepository.findById(5L)).thenReturn(Optional.of(event(EventStatus.PUBLISHED)));
        when(venueRepository.findById(6L)).thenReturn(Optional.of(new Venue("Hall", "City", NOW)));
        when(showRepository.save(any(Show.class))).thenAnswer(inv -> inv.getArgument(0));

        ShowResponse response = showService.createShow(
                new ShowRequest(5L, 6L, START, END, new BigDecimal("250.00"), "INR"));

        assertEquals(ShowStatus.SCHEDULED, response.status());
        assertEquals(new BigDecimal("250.00"), response.price());
        assertEquals("INR", response.currency());
    }

    @Test
    void endTimeBeforeStartTimeIsRejected() {
        when(eventRepository.findById(5L)).thenReturn(Optional.of(event(EventStatus.PUBLISHED)));
        when(venueRepository.findById(6L)).thenReturn(Optional.of(new Venue("Hall", "City", NOW)));

        ApiException ex = assertThrows(ApiException.class, () -> showService.createShow(
                new ShowRequest(5L, 6L, END, START, new BigDecimal("250.00"), "INR")));

        assertEquals("INVALID_SHOW_TIMES", ex.getCode());
        assertEquals(400, ex.getStatus().value());
        verify(showRepository, never()).save(any(Show.class));
    }

    @Test
    void showInThePastIsRejected() {
        when(eventRepository.findById(5L)).thenReturn(Optional.of(event(EventStatus.PUBLISHED)));
        when(venueRepository.findById(6L)).thenReturn(Optional.of(new Venue("Hall", "City", NOW)));

        ApiException ex = assertThrows(ApiException.class, () -> showService.createShow(
                new ShowRequest(5L, 6L, NOW.minusSeconds(7_200), NOW.minusSeconds(3_600),
                        new BigDecimal("250.00"), "INR")));

        assertEquals("INVALID_SHOW_TIMES", ex.getCode());
    }

    @Test
    void unknownEventIsNotFound() {
        when(eventRepository.findById(5L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> showService.createShow(
                new ShowRequest(5L, 6L, START, END, new BigDecimal("250.00"), "INR")));

        assertEquals("EVENT_NOT_FOUND", ex.getCode());
        assertEquals(404, ex.getStatus().value());
    }

    @Test
    void showCannotBeCreatedForCancelledEvent() {
        when(eventRepository.findById(5L)).thenReturn(Optional.of(event(EventStatus.CANCELLED)));

        ApiException ex = assertThrows(ApiException.class, () -> showService.createShow(
                new ShowRequest(5L, 6L, START, END, new BigDecimal("250.00"), "INR")));

        assertEquals("EVENT_CANCELLED", ex.getCode());
    }

    @Test
    void showCannotBeOpenedWhileEventIsNotPublished() {
        when(showRepository.findById(1L)).thenReturn(Optional.of(scheduledShow()));
        when(eventRepository.findById(5L)).thenReturn(Optional.of(event(EventStatus.DRAFT)));

        ApiException ex = assertThrows(ApiException.class, () -> showService.open(1L));

        assertEquals("EVENT_NOT_PUBLISHED", ex.getCode());
    }

    @Test
    void showOpensWhenEventIsPublished() {
        when(showRepository.findById(1L)).thenReturn(Optional.of(scheduledShow()));
        when(eventRepository.findById(5L)).thenReturn(Optional.of(event(EventStatus.PUBLISHED)));

        ShowResponse response = showService.open(1L);

        assertEquals(ShowStatus.OPEN, response.status());
    }

    @Test
    void cancelledShowCannotBeOpenedOrCancelledAgain() {
        Show show = scheduledShow();
        show.changeStatus(ShowStatus.CANCELLED, NOW);
        when(showRepository.findById(1L)).thenReturn(Optional.of(show));

        ApiException ex = assertThrows(ApiException.class, () -> showService.cancel(1L));

        assertEquals("INVALID_STATE_TRANSITION", ex.getCode());
        assertEquals(409, ex.getStatus().value());
    }

    private Event event(EventStatus status) {
        Event e = new Event("Dune", null, EventType.MOVIE, null, NOW);
        ReflectionTestUtils.setField(e, "id", 5L);
        ReflectionTestUtils.setField(e, "status", status);
        return e;
    }

    private Show scheduledShow() {
        Show s = new Show(5L, 6L, START, END, new BigDecimal("250.00"), "INR", NOW);
        ReflectionTestUtils.setField(s, "id", 1L);
        return s;
    }
}
