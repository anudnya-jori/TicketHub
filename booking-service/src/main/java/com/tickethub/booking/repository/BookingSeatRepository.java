package com.tickethub.booking.repository;

import com.tickethub.booking.domain.BookingSeat;
import com.tickethub.booking.domain.SeatHoldStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {

    List<BookingSeat> findByBookingIdOrderBySeatId(Long bookingId);

    List<BookingSeat> findByShowIdAndStatusIn(Long showId, Collection<SeatHoldStatus> statuses);

    List<BookingSeat> findByShowIdAndSeatIdInAndStatusIn(Long showId, Collection<Long> seatIds,
                                                         Collection<SeatHoldStatus> statuses);

    /** Moves every seat of a booking from one status to another, e.g. RESERVED -> RELEASED. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BookingSeat bs set bs.status = :to where bs.bookingId = :bookingId and bs.status = :from")
    int transition(@Param("bookingId") Long bookingId,
                   @Param("from") SeatHoldStatus from,
                   @Param("to") SeatHoldStatus to);
}
