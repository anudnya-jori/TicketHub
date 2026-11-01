package com.tickethub.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickethub.gateway.error.GatewayErrorResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * The gateway is the FIRST line of defence: it validates the JWT before any request reaches a service
 * (each service validates again - defence in depth). The Authorization header is forwarded unchanged.
 * security.enabled=false keeps everything open for local development.
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    @Bean
    @ConditionalOnProperty(name = "security.enabled", havingValue = "true")
    SecurityWebFilterChain securedChain(ServerHttpSecurity http, ObjectMapper objectMapper) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(a -> a
                        .pathMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .pathMatchers(HttpMethod.POST, "/payments/webhook").permitAll()   // signed by the provider
                        .anyExchange().authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> {
                }))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((ex, denied) -> write(objectMapper, ex, HttpStatus.UNAUTHORIZED,
                                "UNAUTHENTICATED", "A valid access token is required."))
                        .accessDeniedHandler((ex, denied) -> write(objectMapper, ex, HttpStatus.FORBIDDEN,
                                "FORBIDDEN", "You are not allowed to do this.")))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "security.enabled", havingValue = "false", matchIfMissing = true)
    SecurityWebFilterChain openChain(ServerHttpSecurity http) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(a -> a.anyExchange().permitAll())
                .build();
    }

    private Mono<Void> write(ObjectMapper mapper, ServerWebExchange exchange, HttpStatus status, String code,
                             String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] bytes = mapper.writeValueAsBytes(new GatewayErrorResponse(Instant.now(), status.value(),
                    status.getReasonPhrase(), code, message, exchange.getRequest().getPath().value(), List.of()));
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (Exception e) {
            return exchange.getResponse().setComplete();
        }
    }
}
