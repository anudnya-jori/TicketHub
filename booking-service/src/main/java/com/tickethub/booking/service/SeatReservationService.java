package com.tickethub.booking.service;

import com.tickethub.booking.domain.Booking;
import com.tickethub.booking.domain.BookingSeat;
import com.tickethub.booking.domain.SeatHoldStatus;
import com.tickethub.booking.dto.SeatStateResponse;
import com.tickethub.booking.repository.BookingSeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Owns the seat-hold rules. A seat is AVAILABLE for a show when NO active hold (RESERVED/BOOKED) exists for it.
 * The write methods use Propagation.MANDATORY: they must run inside the caller's transaction, so a reservation
 * and its booking always commit or roll back together.
 */
@Service
public class SeatReservationService {

    private final BookingSeatRepository bookingSeatRepository;
    private final Clock clock;

    public SeatReservationService(BookingSeatRepository bookingSeatRepository, Clock clock) {
        this.bookingSeatRepository = bookingSeatRepository;
        this.clock = clock;
    }

    /**
     * Inserts one RESERVED row per seat and FLUSHES immediately, so the database's unique index
     * (uq_booking_seat_active) is checked right here. If another transaction already holds a seat, this
     * throws DataIntegrityViolationException and the caller's whole transaction rolls back.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserveSeats(Booking booking, List<Long> seatIds) {
        Instant now = Instant.now(clock);
        List<BookingSeat> holds = seatIds.stream()
                .map(seatId -> new BookingSeat(booking.getId(), booking.getShowId(), seatId, now))
                .toList();
        bookingSeatRepository.saveAllAndFlush(holds);
    }

    /** RESERVED -> RELEASED for every seat of the booking (cancel, expiry, failed payment). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseSeats(Long bookingId) {
        bookingSeatRepository.transition(bookingId, SeatHoldStatus.RESERVED, SeatHoldStatus.RELEASED);
    }

    /** BOOKED -> RELEASED: a customer cancelled a CONFIRMED booking (Scenario 5), the seats go back on sale. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseBookedSeats(Long bookingId) {
        bookingSeatRepository.transition(bookingId, SeatHoldStatus.BOOKED, SeatHoldStatus.RELEASED);
    }

    /** RESERVED -> BOOKED (payment succeeded). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void markBooked(Long bookingId) {
        bookingSeatRepository.transition(bookingId, SeatHoldStatus.RESERVED, SeatHoldStatus.BOOKED);
    }

    /** Seats currently held (RESERVED or BOOKED) for a show. Everything not listed is AVAILABLE. Used by Event/Show. */
    @Transactional(readOnly = true)
    public List<SeatStateResponse> getSeatStates(Long showId) {
        return bookingSeatRepository.findByShowIdAndStatusIn(showId, SeatHoldStatus.ACTIVE).stream()
                .map(h -> new SeatStateResponse(h.getSeatId(), h.getStatus().name()))
                .toList();
    }

    /** Which of the requested seats are already held (friendly early check; the database index is the real guard). */
    @Transactional(readOnly = true)
    public List<Long> findHeldSeatIds(Long showId, List<Long> seatIds) {
        return bookingSeatRepository.findByShowIdAndSeatIdInAndStatusIn(showId, seatIds, SeatHoldStatus.ACTIVE)
                .stream().map(BookingSeat::getSeatId).sorted().toList();
    }

    @Transactional(readOnly = true)
    public List<Long> findSeatIdsByBooking(Long bookingId) {
        return bookingSeatRepository.findByBookingIdOrderBySeatId(bookingId)
                .stream().map(BookingSeat::getSeatId).toList();
    }
}
