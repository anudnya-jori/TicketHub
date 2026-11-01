package com.tickethub.payment.security;

import com.tickethub.payment.controller.PaymentController;
import com.tickethub.payment.dto.CreatePaymentResult;
import com.tickethub.payment.dto.PaymentResponse;
import com.tickethub.payment.dto.WebhookResponse;
import com.tickethub.payment.domain.PaymentStatus;
import com.tickethub.payment.service.PaymentService;
import com.tickethub.payment.service.WebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PaymentController.class, properties = {"security.enabled=true", "internal.api-key=k"})
@Import({SecurityConfig.class, SecurityErrorHandlers.class, WebConfig.class})
class PaymentSecurityTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean PaymentService paymentService;
    @MockitoBean WebhookService webhookService;

    @Test
    void payingRequiresALogin() throws Exception {
        mockMvc.perform(post("/payments").contentType(MediaType.APPLICATION_JSON).content("{\"bookingId\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theCustomerIsTakenFromTheToken() throws Exception {
        when(paymentService.pay(eq("kc-1"), eq(1L), any())).thenReturn(new CreatePaymentResult(
                new PaymentResponse(5L, 1L, new BigDecimal("500.00"), "INR", PaymentStatus.SUCCESS, null,
                        Instant.now(), null, null), true));

        mockMvc.perform(post("/payments")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER"))
                                .jwt(j -> j.subject("kc-1")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bookingId\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void theProviderWebhookIsReachableWithoutALoginBecauseItIsAuthenticatedBySignature() throws Exception {
        when(webhookService.handle(any(), any())).thenReturn(new WebhookResponse("APPLIED"));

        mockMvc.perform(post("/payments/webhook").header("X-Signature", "abc")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("APPLIED"));
    }
}