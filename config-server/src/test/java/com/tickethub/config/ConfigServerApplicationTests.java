package com.tickethub.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ConfigServerApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void servesBookingServiceConfigurationIncludingSharedSettings() throws Exception {
        mockMvc.perform(get("/booking-service/default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("booking-service"))
                .andExpect(jsonPath("$.propertySources[*].name",
                        hasItem(containsString("booking-service.yml"))))
                .andExpect(jsonPath("$.propertySources[*].name",
                        hasItem(containsString("application.yml"))));
    }

    @Test
    void environmentSpecificProfileIsSeparatedFromTheDefault() throws Exception {
        mockMvc.perform(get("/booking-service/demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propertySources[*].name",
                        hasItem(containsString("booking-service-demo.yml"))));
    }

    @Test
    void noSecretsAreStoredInTheConfigurationRepository() throws Exception {
        mockMvc.perform(get("/event-show-service/default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propertySources[*].source['spring.datasource.password']").isEmpty());
    }
}
