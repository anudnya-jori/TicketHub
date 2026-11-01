package com.tickethub.eventshow.integration;

import com.jayway.jsonpath.JsonPath;
import com.tickethub.eventshow.client.BookingClient;
import com.tickethub.eventshow.dto.SeatStateDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test of the whole Event/Show service against a REAL PostgreSQL (Flyway migrations +
 * Hibernate schema validation + controllers + services). Skipped automatically if Docker is not running.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CatalogueFlowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingClient bookingClient;

    @Test
    void adminBuildsCatalogueThenCustomerBrowsesAndSeesSeatAvailability() throws Exception {
        // --- admin: venue + seat layout (ST-03, ST-04)
        long venueId = idOf(postJson("/venues", "{\"name\":\"PVR Cinemas\",\"location\":\"Pune\"}", 201));
        MvcResult seats = postJson("/venues/" + venueId + "/seats",
                "{\"seats\":[{\"row\":\"a\",\"number\":1,\"category\":\"PREMIUM\"},"
                        + "{\"row\":\"A\",\"number\":2},{\"row\":\"A\",\"number\":3}]}", 201);
        long seat1 = JsonPath.<Number>read(seats.getResponse().getContentAsString(), "$[0].id").longValue();
        long seat2 = JsonPath.<Number>read(seats.getResponse().getContentAsString(), "$[1].id").longValue();

        // duplicate seat in the same venue -> business conflict
        mockMvc.perform(post("/venues/" + venueId + "/seats").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seats\":[{\"row\":\"A\",\"number\":1}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_ALREADY_EXISTS"));

        // --- admin: events start in DRAFT
        long eventId = idOf(postJson("/events",
                "{\"title\":\"Dune\",\"description\":\"Sci-fi\",\"type\":\"MOVIE\"}", 201));
        long draftEventId = idOf(postJson("/events", "{\"title\":\"Hidden draft\",\"type\":\"CONCERT\"}", 201));
        mockMvc.perform(get("/events/" + eventId)).andExpect(jsonPath("$.status").value("DRAFT"));

        // --- admin: a show starts SCHEDULED and carries price + currency (ST-46)
        long showId = idOf(postJson("/shows", "{\"eventId\":" + eventId + ",\"venueId\":" + venueId
                + ",\"startTime\":\"2030-01-01T10:00:00Z\",\"endTime\":\"2030-01-01T12:00:00Z\","
                + "\"price\":250.00,\"currency\":\"INR\"}", 201));
        mockMvc.perform(get("/shows/" + showId))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.price").value(250.00))
                .andExpect(jsonPath("$.eventStatus").value("DRAFT"));

        // invalid times -> 400 business error ; unknown event -> 404
        mockMvc.perform(post("/shows").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":" + eventId + ",\"venueId\":" + venueId
                                + ",\"startTime\":\"2030-01-01T12:00:00Z\",\"endTime\":\"2030-01-01T10:00:00Z\","
                                + "\"price\":250,\"currency\":\"INR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SHOW_TIMES"));
        mockMvc.perform(post("/shows").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":999999,\"venueId\":" + venueId
                                + ",\"startTime\":\"2030-01-01T10:00:00Z\",\"endTime\":\"2030-01-01T12:00:00Z\","
                                + "\"price\":250,\"currency\":\"INR\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));

        // --- lifecycle rules (ST-48): cannot open a show of an unpublished event
        mockMvc.perform(post("/shows/" + showId + "/open"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_PUBLISHED"));
        mockMvc.perform(post("/events/" + eventId + "/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        mockMvc.perform(post("/events/" + eventId + "/publish"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        mockMvc.perform(post("/shows/" + showId + "/open"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));

        // --- customer browsing (ST-49): only PUBLISHED events, paginated; unknown id -> 404
        mockMvc.perform(get("/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(eventId));
        mockMvc.perform(get("/events/" + draftEventId)).andExpect(status().isOk());
        mockMvc.perform(get("/events/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
        mockMvc.perform(get("/shows").param("eventId", String.valueOf(eventId)).param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.size").value(5));

        // --- seat availability (ST-05): layout merged with Booking's reservation state
        when(bookingClient.getSeatStates(showId)).thenReturn(List.of());
        mockMvc.perform(get("/shows/" + showId + "/seats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seats.length()").value(3))
                .andExpect(jsonPath("$.seats[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.seats[0].row").value("A"));

        when(bookingClient.getSeatStates(showId)).thenReturn(List.of(new SeatStateDto(seat2, "RESERVED")));
        mockMvc.perform(get("/shows/" + showId + "/seats"))
                .andExpect(jsonPath("$.seats[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.seats[1].seatId").value(seat2))
                .andExpect(jsonPath("$.seats[1].status").value("RESERVED"));

        mockMvc.perform(get("/shows/999999/seats"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHOW_NOT_FOUND"));

        // --- cancelling the event cascades to its shows
        mockMvc.perform(post("/events/" + eventId + "/cancel")).andExpect(status().isOk());
        mockMvc.perform(get("/shows/" + showId)).andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(post("/shows/" + showId + "/open")).andExpect(status().isConflict());

        // unused id kept for readability of the seat assertions above
        org.junit.jupiter.api.Assertions.assertNotEquals(seat1, seat2);
    }

    private MvcResult postJson(String url, String json, int expectedStatus) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    private long idOf(MvcResult result) throws Exception {
        return JsonPath.<Number>read(result.getResponse().getContentAsString(), "$.id").longValue();
    }
}
