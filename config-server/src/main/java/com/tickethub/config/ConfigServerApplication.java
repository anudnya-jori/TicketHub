package com.tickethub.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Centralised configuration (ST-13). Clients ask: GET /{service-name}/{profile}.
 * Try: http://localhost:8888/booking-service/default
 *
 * RULE: this folder is committed to Git, so it must NEVER contain secrets (passwords, tokens, keys).
 * Secrets come from environment variables such as ${DB_PASSWORD}.
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
