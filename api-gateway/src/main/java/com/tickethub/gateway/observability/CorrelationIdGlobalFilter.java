package com.tickethub.gateway.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Correlation id (ST-25). The gateway is where a request enters, so it makes the id: a client-supplied
 * X-Correlation-Id is kept (if sane), otherwise a new one is generated. The id is forwarded to the service and echoed in
 * the response, so a customer can quote it and an engineer can search it in Kibana.
 */
@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    public static final String HEADER = "X-Correlation-Id";
    private static final Logger log = LoggerFactory.getLogger(CorrelationIdGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String id = exchange.getRequest().getHeaders().getFirst(HEADER);
        if (id == null || id.isBlank() || id.length() > 100) {
            id = UUID.randomUUID().toString();
        }
        final String correlationId = id;
        ServerHttpRequest request = exchange.getRequest().mutate().header(HEADER, correlationId).build();
        exchange.getResponse().getHeaders().set(HEADER, correlationId);
        // only method + path + id are logged: never headers (they contain the bearer token)
        log.info("GATEWAY_REQUEST correlationId={} {} {}", correlationId, request.getMethod(), request.getPath().value());
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
