package com.tickethub.gateway.observability;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CorrelationIdGlobalFilterTest {

    private final CorrelationIdGlobalFilter filter = new CorrelationIdGlobalFilter();

    private String forwardedId(MockServerHttpRequest request, AtomicReference<ServerWebExchange> seen) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = e -> {
            seen.set(e);
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        assertEquals(seen.get().getRequest().getHeaders().getFirst("X-Correlation-Id"),
                exchange.getResponse().getHeaders().getFirst("X-Correlation-Id"));
        return seen.get().getRequest().getHeaders().getFirst("X-Correlation-Id");
    }

    @Test
    void generatesAnIdWhenTheClientSentNone() {
        String id = forwardedId(MockServerHttpRequest.get("/events").build(), new AtomicReference<>());
        assertNotNull(id);
    }

    @Test
    void keepsAnIdSuppliedByTheClient() {
        String id = forwardedId(MockServerHttpRequest.get("/events").header("X-Correlation-Id", "abc-123").build(),
                new AtomicReference<>());
        assertEquals("abc-123", id);
    }

    @Test
    void replacesAnAbsurdlyLongId() {
        String id = forwardedId(MockServerHttpRequest.get("/events").header("X-Correlation-Id", "x".repeat(500)).build(),
                new AtomicReference<>());
        assertEquals(36, id.length());     // a fresh UUID
    }
}
