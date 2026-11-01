package com.tickethub.notification.controller;

import com.tickethub.notification.dto.NotificationResponse;
import com.tickethub.notification.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Internal only (not routed by the gateway): lets operators/tests see what was sent for a booking. */
@RestController
@RequestMapping("/internal/notifications")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public List<NotificationResponse> byBooking(@RequestParam Long bookingId) {
        return service.findByBooking(bookingId);
    }
}
