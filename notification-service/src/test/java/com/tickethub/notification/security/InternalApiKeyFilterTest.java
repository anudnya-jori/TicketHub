package com.tickethub.notification.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalApiKeyFilterTest {

    private final InternalApiKeyFilter filter = new InternalApiKeyFilter("secret");

    private MockHttpServletResponse call(String uri, String key) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
        req.setRequestURI(uri);
        if (key != null) {
            req.addHeader("X-Internal-Api-Key", key);
        }
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }

    @Test
    void missingOrWrongKeyIsRejectedWith401() throws Exception {
        assertEquals(401, call("/internal/notifications", null).getStatus());
        MockHttpServletResponse wrong = call("/internal/notifications", "nope");
        assertEquals(401, wrong.getStatus());
        assertTrue(wrong.getContentAsString().contains("INVALID_INTERNAL_KEY"));
    }

    @Test
    void correctKeyAndNonInternalPathsPass() throws Exception {
        assertEquals(200, call("/internal/notifications", "secret").getStatus());
        assertEquals(200, call("/status", null).getStatus());
    }
}
