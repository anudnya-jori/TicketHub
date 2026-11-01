package com.tickethub.payment.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("TicketHub - Payment Service")
                .version("0.1.0")
                .description("Payments (skeleton in v1; simulated payment arrives in sprint S5). Swagger UI: /swagger-ui.html"));
    }
}
