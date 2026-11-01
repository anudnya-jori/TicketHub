package com.tickethub.eventshow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VenueRequest(@NotBlank @Size(max = 200) String name, @Size(max = 300) String location) {
}
