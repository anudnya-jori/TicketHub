package com.tickethub.payment.observability;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void anIncomingIdIsKeptAndEchoed() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Correlation-Id", "abc-123");
        MockHttpServletResponse res = new MockHttpServletResponse();
        String[] seenInsideTheRequest = new String[1];

        filter.doFilter(req, res, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest rq, jakarta.servlet.ServletResponse rs) {
                seenInsideTheRequest[0] = MDC.get("correlationId");
            }
        });

        assertEquals("abc-123", seenInsideTheRequest[0]);          // present in logs while handling
        assertEquals("abc-123", res.getHeader("X-Correlation-Id"));
        assertNull(MDC.get("correlationId"));                       // cleaned up afterwards (no leak between requests)
    }

    @Test
    void aMissingIdIsGenerated() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), res, new MockFilterChain());
        assertNotNull(res.getHeader("X-Correlation-Id"));
        assertEquals(36, res.getHeader("X-Correlation-Id").length());   // a UUID
    }
}
