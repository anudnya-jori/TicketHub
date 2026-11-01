package com.tickethub.eventshow.service;

import com.tickethub.eventshow.domain.Seat;
import com.tickethub.eventshow.domain.SeatCategory;
import com.tickethub.eventshow.domain.Venue;
import com.tickethub.eventshow.dto.AddSeatsRequest;
import com.tickethub.eventshow.dto.SeatRequest;
import com.tickethub.eventshow.dto.SeatResponse;
import com.tickethub.eventshow.dto.VenueRequest;
import com.tickethub.eventshow.dto.VenueResponse;
import com.tickethub.eventshow.exception.ApiException;
import com.tickethub.eventshow.repository.SeatRepository;
import com.tickethub.eventshow.repository.VenueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class VenueService {

    private final VenueRepository venueRepository;
    private final SeatRepository seatRepository;
    private final Clock clock;

    public VenueService(VenueRepository venueRepository, SeatRepository seatRepository, Clock clock) {
        this.venueRepository = venueRepository;
        this.seatRepository = seatRepository;
        this.clock = clock;
    }

    @Transactional
    public VenueResponse createVenue(VenueRequest request) {
        Venue venue = new Venue(request.name().trim(), request.location(), Instant.now(clock));
        return VenueResponse.from(venueRepository.save(venue));
    }

    @Transactional
    public VenueResponse updateVenue(Long id, VenueRequest request) {
        Venue venue = findVenue(id);
        venue.update(request.name().trim(), request.location());
        return VenueResponse.from(venue);
    }

    @Transactional(readOnly = true)
    public VenueResponse getVenue(Long id) {
        return VenueResponse.from(findVenue(id));
    }

    /**
     * Defines (part of) a venue's physical seat layout. A seat belongs to the venue, not to a show.
     * Rows are normalised to upper case so "a" and "A" are the same row.
     */
    @Transactional
    public List<SeatResponse> addSeats(Long venueId, AddSeatsRequest request) {
        findVenue(venueId);

        Set<String> seenInRequest = new HashSet<>();
        List<Seat> toSave = new ArrayList<>();
        for (SeatRequest s : request.seats()) {
            String row = s.row().trim().toUpperCase();
            String key = row + "#" + s.number();
            if (!seenInRequest.add(key)) {
                throw ApiException.badRequest("DUPLICATE_SEAT_IN_REQUEST",
                        "Seat " + row + s.number() + " appears more than once in the request.");
            }
            if (seatRepository.existsByVenueIdAndRowLabelAndSeatNumber(venueId, row, s.number())) {
                throw ApiException.conflict("SEAT_ALREADY_EXISTS",
                        "Seat " + row + s.number() + " already exists in venue " + venueId + ".");
            }
            SeatCategory category = s.category() != null ? s.category() : SeatCategory.STANDARD;
            toSave.add(new Seat(venueId, row, s.number(), category));
        }
        return seatRepository.saveAll(toSave).stream().map(SeatResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<SeatResponse> getSeats(Long venueId) {
        findVenue(venueId);
        return seatRepository.findByVenueIdOrderByRowLabelAscSeatNumberAsc(venueId)
                .stream().map(SeatResponse::from).toList();
    }

    private Venue findVenue(Long id) {
        return venueRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("VENUE_NOT_FOUND", "Venue " + id + " not found."));
    }
}
