package com.tickethub.notification.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("TicketHub - Notification Service")
                .version("0.1.0")
                .description("Notifications (skeleton in v1; Kafka consumer arrives in sprint S9). Swagger UI: /swagger-ui.html"));
    }
}
