package com.tickethub.payment.client;

import com.tickethub.payment.exception.ApiException;
import com.tickethub.payment.resilience.ResilientCaller;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Payment -> Booking (synchronous READ only). Payment RESULTS travel as events through the outbox (Phase 2). */
@Component
public class BookingClient {

    private static final Logger log = LoggerFactory.getLogger(BookingClient.class);

    private final RestClient.Builder builder;
    private final String baseUrl;
    private final ResilientCaller resilient;
    private final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    private volatile RestClient restClient;

    public BookingClient(RestClient.Builder builder,
                         ResilientCaller resilient,
                         @Value("${clients.booking.base-url}") String baseUrl,
                         @Value("${clients.booking.connect-timeout-ms}") int connectTimeoutMs,
                         @Value("${clients.booking.read-timeout-ms}") int readTimeoutMs) {
        this.builder = builder;
        this.baseUrl = baseUrl;
        this.resilient = resilient;
        this.factory.setConnectTimeout(connectTimeoutMs);
        this.factory.setReadTimeout(readTimeoutMs);
    }

    private RestClient client() {
        RestClient c = restClient;
        if (c == null) {
            synchronized (this) {
                c = restClient;
                if (c == null) {
                    c = builder.clone().baseUrl(baseUrl).requestFactory(factory).build();
                    restClient = c;
                }
            }
        }
        return c;
    }

    public BookingDto getBooking(Long bookingId) {
        try {
            BookingDto dto = resilient.read("booking-service", () -> client().get()
                    .uri("/internal/bookings/{id}", bookingId).retrieve().body(BookingDto.class));
            if (dto == null) {
                throw unavailable();
            }
            return dto;
        } catch (HttpClientErrorException.NotFound e) {
            throw ApiException.notFound("BOOKING_NOT_FOUND", "Booking " + bookingId + " not found.");
        } catch (CallNotPermittedException e) {
            log.warn("Booking circuit breaker is OPEN: failing fast (getBooking {})", bookingId);
            throw unavailable();
        } catch (RestClientException e) {
            log.warn("Booking service call failed (getBooking {}): {}", bookingId, e.getMessage());
            throw unavailable();
        }
    }

    private ApiException unavailable() {
        return ApiException.unavailable("BOOKING_SERVICE_UNAVAILABLE",
                "Booking information is temporarily unavailable. Please try again later.");
    }
}
