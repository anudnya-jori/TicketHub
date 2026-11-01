package com.tickethub.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ApiGatewayApplicationTests {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void unknownPathReturnsControlledRouteNotFoundError() {
        webTestClient.get().uri("/nothing-here")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ROUTE_NOT_FOUND")
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.path").isEqualTo("/nothing-here");
    }

    @Test
    void internalEndpointsAreNotReachableThroughTheGateway() {
        webTestClient.get().uri("/internal/shows/1/seat-states")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ROUTE_NOT_FOUND");
    }

    @Test
    void routeToAServiceWithNoRegisteredInstanceReturnsControlled503() {
        webTestClient.get().uri("/bookings/1")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE");
    }
}
