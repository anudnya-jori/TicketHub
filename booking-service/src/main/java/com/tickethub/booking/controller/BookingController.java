package com.tickethub.booking.controller;

import com.tickethub.booking.dto.BookingResponse;
import com.tickethub.booking.dto.CreateBookingRequest;
import com.tickethub.booking.dto.CreateBookingResult;
import com.tickethub.booking.security.CustomerEmail;
import com.tickethub.booking.security.CustomerId;
import com.tickethub.booking.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Customer-facing booking API.
 * The customer's identity comes from the verified JWT ("sub"). Only when security.enabled=false (local dev/tests)
 * does the X-Customer-Id header stand in for it - see security/CallerArgumentResolver.
 */
@RestController
@RequestMapping("/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /** 201 for a new booking; 200 with the ORIGINAL booking when the same Idempotency-Key is retried. */
    @PostMapping
    public ResponseEntity<BookingResponse> create(@CustomerId String customerId,
                                                  @CustomerEmail String customerEmail,
                                                  @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                  @Valid @RequestBody CreateBookingRequest request) {
        CreateBookingResult result = bookingService.createBooking(customerId, customerEmail, idempotencyKey, request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.booking());
    }

    @GetMapping("/{id}")
    public BookingResponse get(@CustomerId String customerId, @PathVariable Long id) {
        return bookingService.getBooking(id, customerId);
    }

    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(@CustomerId String customerId, @PathVariable Long id) {
        return bookingService.cancelBooking(id, customerId);
    }
}
