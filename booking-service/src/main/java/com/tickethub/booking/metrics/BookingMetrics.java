package com.tickethub.booking.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Business metrics (ST-26): numbers that answer "is the BUSINESS healthy?", not just "is the JVM alive?".
 * Exposed at /actuator/prometheus as tickethub_bookings_*_total and shown in Grafana.
 */
@Component
public class BookingMetrics {

    private final Counter created;
    private final Counter confirmed;
    private final Counter expired;
    private final Counter cancelled;
    private final Counter seatConflicts;

    public BookingMetrics(MeterRegistry registry) {
        this.created = Counter.builder("tickethub.bookings.created").description("Bookings created").register(registry);
        this.confirmed = Counter.builder("tickethub.bookings.confirmed").description("Bookings confirmed after payment").register(registry);
        this.expired = Counter.builder("tickethub.bookings.expired").description("Unpaid bookings expired").register(registry);
        this.cancelled = Counter.builder("tickethub.bookings.cancelled").description("Bookings cancelled by customers").register(registry);
        this.seatConflicts = Counter.builder("tickethub.bookings.seat_conflicts").description("Booking attempts rejected because a seat was taken").register(registry);
    }

    public void created() { created.increment(); }
    public void confirmed() { confirmed.increment(); }
    public void expired() { expired.increment(); }
    public void cancelled() { cancelled.increment(); }
    public void seatConflict() { seatConflicts.increment(); }
}
