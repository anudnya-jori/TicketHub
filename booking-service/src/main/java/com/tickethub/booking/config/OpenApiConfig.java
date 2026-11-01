package com.tickethub.booking.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("TicketHub - Booking Service")
                .version("0.1.0")
                .description("Bookings and seat reservation (skeleton in v1; reservation logic arrives in sprint S4). Swagger UI: /swagger-ui.html"));
    }
}
