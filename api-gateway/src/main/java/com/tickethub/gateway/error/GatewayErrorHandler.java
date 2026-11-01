package com.tickethub.gateway.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Turns routing failures into controlled JSON errors (ST-12): no route (404), no healthy instance (503),
 * connection refused (502), timeout (504). Never a stack trace.
 */
@Component
@Order(-2)   // run before Spring Boot's default error handler
public class GatewayErrorHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorHandler.class);

    private final ObjectMapper objectMapper;

    public GatewayErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus status;
        String code;
        String message;

        if (ex instanceof ResponseStatusException rse) {
            HttpStatus resolved = HttpStatus.resolve(rse.getStatusCode().value());
            status = resolved != null ? resolved : HttpStatus.INTERNAL_SERVER_ERROR;
            if (status == HttpStatus.NOT_FOUND) {
                code = "ROUTE_NOT_FOUND";
                message = "No route matches this request.";
            } else if (status == HttpStatus.SERVICE_UNAVAILABLE) {
                code = "SERVICE_UNAVAILABLE";
                message = "The requested service is not available right now. Please try again later.";
            } else if (status == HttpStatus.GATEWAY_TIMEOUT) {
                code = "GATEWAY_TIMEOUT";
                message = "The service took too long to respond.";
            } else {
                code = status.name();
                message = status.is4xxClientError() ? status.getReasonPhrase() : "The gateway could not process the request.";
            }
        } else if (isCause(ex, TimeoutException.class)) {
            status = HttpStatus.GATEWAY_TIMEOUT;
            code = "GATEWAY_TIMEOUT";
            message = "The service took too long to respond.";
        } else if (isCause(ex, ConnectException.class)) {
            status = HttpStatus.BAD_GATEWAY;
            code = "BAD_GATEWAY";
            message = "Could not connect to the service.";
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            code = "INTERNAL_ERROR";
            message = "An unexpected error occurred.";
        }

        if (status.is5xxServerError()) {
            log.warn("Gateway error {} on {}: {}", status.value(), exchange.getRequest().getPath(), ex.toString());
        }

        GatewayErrorResponse body = new GatewayErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(),
                code, message, exchange.getRequest().getPath().value(), List.of());

        exchange.getResponse().setStatusCode(HttpStatusCode.valueOf(status.value()));
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            bytes = "{\"code\":\"INTERNAL_ERROR\"}".getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private boolean isCause(Throwable ex, Class<? extends Throwable> type) {
        Throwable t = ex;
        while (t != null) {
            if (type.isInstance(t)) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }
}
