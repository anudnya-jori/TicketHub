package com.tickethub.eventshow.dto;

import com.tickethub.eventshow.domain.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Create/update an event. Status is NOT accepted from the client: it is set by the server. */
public record EventRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        @NotNull EventType type,
        @Size(max = 500) String posterRef) {
}
