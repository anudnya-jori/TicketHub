package com.tickethub.eventshow.controller;

import com.tickethub.eventshow.domain.EventStatus;
import com.tickethub.eventshow.domain.EventType;
import com.tickethub.eventshow.dto.EventResponse;
import com.tickethub.eventshow.exception.ApiException;
import com.tickethub.eventshow.service.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API-level tests of the HTTP contract and the standard error format (no database needed). */
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(EventController.class)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;

    @Test
    void createEventReturns201AndDraftStatus() throws Exception {
        when(eventService.createEvent(any())).thenReturn(new EventResponse(1L, "Dune", "Sci-fi",
                EventType.MOVIE, EventStatus.DRAFT, null, Instant.parse("2026-10-23T00:00:00Z")));

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Dune\",\"description\":\"Sci-fi\",\"type\":\"MOVIE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void blankTitleIsRejectedWithFieldLevelDetails() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"type\":\"MOVIE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value("/events"))
                .andExpect(jsonPath("$.details[0].field").value("title"));
    }

    @Test
    void unknownEventTypeValueIsRejectedAsMalformedRequest() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Dune\",\"type\":\"NOT_A_TYPE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void unknownEventReturns404WithStandardError() throws Exception {
        when(eventService.getEvent(99L)).thenThrow(ApiException.notFound("EVENT_NOT_FOUND", "Event 99 not found."));

        mockMvc.perform(get("/events/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/events/99"));
    }

    @Test
    void nonNumericIdReturns400() throws Exception {
        mockMvc.perform(get("/events/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void unexpectedFailureReturns500WithoutLeakingInternals() throws Exception {
        when(eventService.getEvent(1L)).thenThrow(new IllegalStateException("secret internal detail"));

        mockMvc.perform(get("/events/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message", not(containsString("secret"))));
    }
}
