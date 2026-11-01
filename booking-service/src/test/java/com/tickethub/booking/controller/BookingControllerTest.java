package com.tickethub.booking.controller;

import com.tickethub.booking.domain.BookingStatus;
import com.tickethub.booking.dto.BookingResponse;
import com.tickethub.booking.dto.CreateBookingResult;
import com.tickethub.booking.exception.ApiException;
import com.tickethub.booking.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP contract of the Booking API: status codes, required headers, validation, error format. */
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(BookingController.class)
class BookingControllerTest {

    private static final String BODY = "{\"showId\":10,\"seatIds\":[1,2]}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingService bookingService;

    private BookingResponse sample() {
        return new BookingResponse(7L, "cust-1", 10L, List.of(1L, 2L), new BigDecimal("500.00"), "INR",
                BookingStatus.PENDING_PAYMENT, Instant.parse("2030-01-01T10:10:00Z"),
                Instant.parse("2030-01-01T10:00:00Z"), "NONE");
    }

    @Test
    void newBookingReturns201() throws Exception {
        when(bookingService.createBooking(eq("cust-1"), any(), eq("key-1"), any()))
                .thenReturn(new CreateBookingResult(sample(), true));

        mockMvc.perform(post("/bookings").header("X-Customer-Id", "cust-1").header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId").value(7))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.amount").value(500.00));
    }

    @Test
    void retryWithSameKeyReturns200AndTheOriginalBooking() throws Exception {
        when(bookingService.createBooking(eq("cust-1"), any(), eq("key-1"), any()))
                .thenReturn(new CreateBookingResult(sample(), false));

        mockMvc.perform(post("/bookings").header("X-Customer-Id", "cust-1").header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(7));
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        mockMvc.perform(post("/bookings").header("X-Customer-Id", "cust-1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void emptySeatListFailsValidationWithFieldDetails() throws Exception {
        mockMvc.perform(post("/bookings").header("X-Customer-Id", "cust-1").header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"showId\":10,\"seatIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("seatIds"));
    }

    @Test
    void seatConflictIsReturnedAs409WithBusinessCode() throws Exception {
        when(bookingService.createBooking(any(), any(), any(), any()))
                .thenThrow(ApiException.conflict("SEAT_UNAVAILABLE", "Seat(s) 1 are not available for this show."));

        mockMvc.perform(post("/bookings").header("X-Customer-Id", "cust-2").header("Idempotency-Key", "key-2")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_UNAVAILABLE"))
                .andExpect(jsonPath("$.path").value("/bookings"));
    }

    @Test
    void viewingAnUnknownBookingReturns404() throws Exception {
        when(bookingService.getBooking(99L, "cust-1"))
                .thenThrow(ApiException.notFound("BOOKING_NOT_FOUND", "Booking 99 not found."));

        mockMvc.perform(get("/bookings/99").header("X-Customer-Id", "cust-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
    }

    @Test
    void cancellingAConfirmedBookingIsRejectedWith409() throws Exception {
        when(bookingService.cancelBooking(7L, "cust-1"))
                .thenThrow(ApiException.conflict("CANCELLATION_NOT_ALLOWED", "A confirmed booking cannot be cancelled."));

        mockMvc.perform(post("/bookings/7/cancel").header("X-Customer-Id", "cust-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANCELLATION_NOT_ALLOWED"));
    }
}
