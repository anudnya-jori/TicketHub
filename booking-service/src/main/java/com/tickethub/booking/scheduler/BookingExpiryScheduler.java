package com.tickethub.booking.scheduler;

import com.tickethub.booking.service.BookingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically frees seats held by unpaid bookings (ST-09). The window itself is configuration
 * (booking.reservation-minutes); this only decides how often we look.
 */
@Component
@ConditionalOnProperty(name = "booking.expiry.enabled", havingValue = "true", matchIfMissing = true)
public class BookingExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(BookingExpiryScheduler.class);

    private final BookingService bookingService;

    public BookingExpiryScheduler(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Scheduled(fixedDelayString = "${booking.expiry.scan-interval-ms:30000}",
            initialDelayString = "${booking.expiry.initial-delay-ms:5000}")
    public void expireOverdueBookings() {
        int expired = bookingService.expireOverdueBookings();
        if (expired > 0) {
            log.info("Expired {} unpaid booking(s) and released their seats", expired);
        }
    }
}
