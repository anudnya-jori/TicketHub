package com.tickethub.eventshow.dto;

import com.tickethub.eventshow.domain.Venue;

public record VenueResponse(Long id, String name, String location, boolean active) {

    public static VenueResponse from(Venue v) {
        return new VenueResponse(v.getId(), v.getName(), v.getLocation(), v.isActive());
    }
}
