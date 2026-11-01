package com.tickethub.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The single entry point for clients (ST-12). Clients call http://localhost:8080/...; the gateway looks the
 * target service up in Eureka by name (lb://service-name) and forwards the request.
 */
@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
