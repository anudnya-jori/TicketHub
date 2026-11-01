package com.tickethub.eventshow.client;

import com.tickethub.eventshow.dto.SeatStateDto;
import com.tickethub.eventshow.exception.ApiException;
import com.tickethub.eventshow.resilience.ResilientCaller;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Calls Booking Service over REST. Booking owns seat reservation state, so Event/Show must ask it.
 * If Booking is down we fail clearly (503) rather than guess availability.
 */
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

    /** Built on first use (not in the constructor) so the load-balancer is fully wired before we create it. */
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

    public List<SeatStateDto> getSeatStates(Long showId) {
        try {
            List<SeatStateDto> states = resilient.read("booking-service", () -> client().get()
                    .uri("/internal/shows/{showId}/seat-states", showId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<SeatStateDto>>() {
                    }));
            return states != null ? states : List.of();
        } catch (CallNotPermittedException e) {
            log.warn("Booking circuit breaker is OPEN: failing fast (show {})", showId);
            throw ApiException.unavailable("BOOKING_SERVICE_UNAVAILABLE",
                    "Seat availability is temporarily unavailable. Please try again later.");
        } catch (RestClientException e) {
            log.warn("Booking service call failed for show {}: {}", showId, e.getMessage());
            throw ApiException.unavailable("BOOKING_SERVICE_UNAVAILABLE",
                    "Seat availability is temporarily unavailable. Please try again later.");
        }
    }
}
