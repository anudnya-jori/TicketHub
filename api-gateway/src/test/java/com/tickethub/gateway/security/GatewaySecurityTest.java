package com.tickethub.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "security.enabled=true")
@AutoConfigureWebTestClient
class GatewaySecurityTest {

    @Autowired WebTestClient client;
    @MockitoBean ReactiveJwtDecoder decoder;

    @Test
    void requestsWithoutATokenNeverReachAService() {
        client.get().uri("/bookings/1").exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void theProviderWebhookIsNotBehindALogin() {
        // no token needed: the request passes security and fails later (no payment service running) with 5xx, not 401
        client.post().uri("/payments/webhook").bodyValue("{}").exchange()
                .expectStatus().value(s -> org.junit.jupiter.api.Assertions.assertNotEquals(401, s.intValue()));
    }
}
