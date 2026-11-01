package com.tickethub.eventshow.security;

import com.tickethub.eventshow.controller.EventController;
import com.tickethub.eventshow.controller.InternalShowController;
import com.tickethub.eventshow.dto.PageResponse;
import com.tickethub.eventshow.service.EventService;
import com.tickethub.eventshow.service.ShowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ADMIN manages the catalogue, CUSTOMER only reads it, nobody anonymous gets in (ST-15). */
@WebMvcTest(controllers = {EventController.class, InternalShowController.class},
        properties = {"security.enabled=true", "internal.api-key=test-key"})
@Import({SecurityConfig.class, SecurityErrorHandlers.class})
class CatalogueSecurityTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean EventService eventService;
    @MockitoBean ShowService showService;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor role(String role) {
        // jwt() builds authorities from "scope" by default; the app converter (realm_access.roles) is not used in MockMvc,
        // so the role is granted explicitly, exactly as the converter would.
        return jwt().jwt(j -> j.subject("u").claim("realm_access", Map.of("roles", List.of(role))))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void anonymousRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/events")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void customerCanBrowseButCannotCreateEvents() throws Exception {
        when(eventService.listPublished(anyInt(), anyInt())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/events").with(role("CUSTOMER"))).andExpect(status().isOk());
        mockMvc.perform(post("/events").with(role("CUSTOMER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"X\",\"type\":\"MOVIE\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminPassesTheAuthorizationRule() throws Exception {
        // service is mocked (returns null) - we only care that the ADMIN is NOT stopped by security (not 401/403)
        mockMvc.perform(post("/events").with(role("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"X\",\"type\":\"MOVIE\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void internalSeatLayoutNeedsTheServiceKey() throws Exception {
        mockMvc.perform(get("/internal/shows/1/seat-layout").with(role("ADMIN")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_INTERNAL_KEY"));
    }
}
