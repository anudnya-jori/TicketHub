package com.tickethub.booking.security;

import com.tickethub.booking.controller.BookingController;
import com.tickethub.booking.controller.InternalBookingController;
import com.tickethub.booking.controller.StatusController;
import com.tickethub.booking.domain.BookingStatus;
import com.tickethub.booking.dto.BookingResponse;
import com.tickethub.booking.dto.CreateBookingResult;
import com.tickethub.booking.service.BookingService;
import com.tickethub.booking.service.SeatReservationService;
import com.tickethub.booking.service.StatusService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** With security ON: who may call what, and where the customer identity comes from. */
@WebMvcTest(controllers = {BookingController.class, InternalBookingController.class, StatusController.class},
        properties = {"security.enabled=true", "internal.api-key=test-key"})
@Import({SecurityConfig.class, SecurityErrorHandlers.class, WebConfig.class})
class BookingSecurityTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean BookingService bookingService;
    @MockitoBean SeatReservationService seatReservationService;
    @MockitoBean StatusService statusService;

    private static final String BODY = "{\"showId\":1,\"seatIds\":[1]}";

    private BookingResponse sample() {
        return new BookingResponse(1L, "kc-user-1", 1L, List.of(1L), new BigDecimal("250.00"), "INR",
                BookingStatus.PENDING_PAYMENT, Instant.now().plusSeconds(600), Instant.now(), "NONE");
    }

    @Test
    void noTokenMeans401InTheStandardErrorFormat() throws Exception {
        mockMvc.perform(post("/bookings").header("Idempotency-Key", "k").contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.path").value("/bookings"));
    }

    @Test
    void customerIdentityComesFromTheTokenNotFromAHeader() throws Exception {
        when(bookingService.createBooking(eq("kc-user-1"), eq("alice@example.com"), eq("k"), any()))
                .thenReturn(new CreateBookingResult(sample(), true));

        mockMvc.perform(post("/bookings")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER"))
                                .jwt(j -> j.subject("kc-user-1").claim("email", "alice@example.com")))
                        .header("X-Customer-Id", "someone-else")          // a spoofed header must be ignored
                        .header("Idempotency-Key", "k").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());

        verify(bookingService).createBooking(eq("kc-user-1"), eq("alice@example.com"), eq("k"), any());
    }

    @Test
    void aTokenWithoutACustomerOrAdminRoleIsForbidden() throws Exception {
        mockMvc.perform(get("/bookings/1").with(jwt().jwt(j -> j.subject("u")
                        .claim("realm_access", java.util.Map.of("roles", List.of("SOMETHING_ELSE"))))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void internalEndpointsNeedTheServiceKeyNotAUserToken() throws Exception {
        mockMvc.perform(get("/internal/bookings/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_INTERNAL_KEY"));
        mockMvc.perform(get("/internal/bookings/1").header("X-Internal-Api-Key", "wrong"))
                .andExpect(status().isUnauthorized());

        when(bookingService.getBookingForService(1L)).thenReturn(sample());
        mockMvc.perform(get("/internal/bookings/1").header("X-Internal-Api-Key", "test-key"))
                .andExpect(status().isOk());
    }

    @Test
    void statusEndpointStaysOpenForHealthChecks() throws Exception {
        mockMvc.perform(get("/status")).andExpect(status().isOk());
    }
}
