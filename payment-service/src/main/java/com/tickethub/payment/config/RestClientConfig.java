package com.tickethub.payment.config;

import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    /**
     * RestClient builder used for calls to other services:
     *  - http://service-name is resolved through Eureka (@LoadBalanced);
     *  - every call carries the internal service key (/internal/** needs it);
     *  - every call carries the caller's correlation id (ST-25), so one id follows the whole request;
     *  - Spring Boot's RestClientCustomizers are applied, otherwise tracing/metrics would silently skip these calls
     *    (defining our own builder bean switches Boot's auto-configured one off).
     */
    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder(@Value("${internal.api-key}") String internalApiKey,
                                                            ObjectProvider<RestClientCustomizer> customizers) {
        RestClient.Builder builder = RestClient.builder()
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .requestInterceptor((request, body, execution) -> {
                    String correlationId = MDC.get("correlationId");
                    if (correlationId != null) {
                        request.getHeaders().set("X-Correlation-Id", correlationId);
                    }
                    return execution.execute(request, body);
                });
        customizers.orderedStream().forEach(c -> c.customize(builder));
        return builder;
    }
}
