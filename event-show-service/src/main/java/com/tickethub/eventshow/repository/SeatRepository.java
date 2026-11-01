package com.tickethub.eventshow.repository;

import com.tickethub.eventshow.domain.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByVenueIdOrderByRowLabelAscSeatNumberAsc(Long venueId);

    boolean existsByVenueIdAndRowLabelAndSeatNumber(Long venueId, String rowLabel, int seatNumber);
}
