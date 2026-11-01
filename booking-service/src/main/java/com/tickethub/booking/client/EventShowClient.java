package com.tickethub.booking.client;

import com.tickethub.booking.exception.ApiException;
import com.tickethub.booking.resilience.ResilientCaller;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Booking -> Event/Show. Synchronous, because Booking needs the answer NOW to decide whether to accept the booking.
 * Uses its own DTOs (ShowDto, SeatLayoutDto): no classes are shared between services.
 * The base URL is a service NAME; Eureka + the load balancer turn it into a real host:port.
 */
@Component
public class EventShowClient {

    private static final Logger log = LoggerFactory.getLogger(EventShowClient.class);

    private final RestClient.Builder builder;
    private final String baseUrl;
    private final ResilientCaller resilient;
    private final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    private volatile RestClient restClient;

    public EventShowClient(RestClient.Builder builder,
                           ResilientCaller resilient,
                           @Value("${clients.event-show.base-url}") String baseUrl,
                           @Value("${clients.event-show.connect-timeout-ms}") int connectTimeoutMs,
                           @Value("${clients.event-show.read-timeout-ms}") int readTimeoutMs) {
        this.builder = builder;
        this.baseUrl = baseUrl;
        this.resilient = resilient;
        this.factory.setConnectTimeout(connectTimeoutMs);
        this.factory.setReadTimeout(readTimeoutMs);
    }

    /** Built on first use so the load balancer is fully wired before the client exists. */
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

    public ShowDto getShow(Long showId) {
        try {
            ShowDto show = resilient.read("event-show-service",
                    () -> client().get().uri("/internal/shows/{id}", showId).retrieve().body(ShowDto.class));
            if (show == null) {
                throw unavailable();
            }
            return show;
        } catch (HttpClientErrorException.NotFound e) {
            throw ApiException.notFound("SHOW_NOT_FOUND", "Show " + showId + " not found.");
        } catch (CallNotPermittedException e) {
            log.warn("Event/Show circuit breaker is OPEN: failing fast (getShow {})", showId);
            throw unavailable();
        } catch (RestClientException e) {
            log.warn("Event/Show service call failed (getShow {}): {}", showId, e.getMessage());
            throw unavailable();
        }
    }

    /** Ids of the seats that physically exist in the show's venue. */
    public List<Long> getSeatLayoutIds(Long showId) {
        try {
            List<SeatLayoutDto> layout = resilient.read("event-show-service", () -> client().get()
                    .uri("/internal/shows/{id}/seat-layout", showId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<SeatLayoutDto>>() {
                    }));
            if (layout == null) {
                throw unavailable();
            }
            return layout.stream().map(SeatLayoutDto::id).toList();
        } catch (HttpClientErrorException.NotFound e) {
            throw ApiException.notFound("SHOW_NOT_FOUND", "Show " + showId + " not found.");
        } catch (CallNotPermittedException e) {
            log.warn("Event/Show circuit breaker is OPEN: failing fast (seat layout {})", showId);
            throw unavailable();
        } catch (RestClientException e) {
            log.warn("Event/Show service call failed (seat layout {}): {}", showId, e.getMessage());
            throw unavailable();
        }
    }

    private ApiException unavailable() {
        return ApiException.unavailable("EVENT_SHOW_SERVICE_UNAVAILABLE",
                "Show information is temporarily unavailable. Please try again later.");
    }
}
