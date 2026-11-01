package com.tickethub.payment.security;

import com.tickethub.payment.exception.ApiException;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves {@link CustomerId} / {@link CustomerEmail}. The customer's identity comes from the verified token,
 * never from something the client typed - that is what makes "you can only see your own bookings" trustworthy.
 */
public class CallerArgumentResolver implements HandlerMethodArgumentResolver {

    private final boolean securityEnabled;

    public CallerArgumentResolver(boolean securityEnabled) {
        this.securityEnabled = securityEnabled;
    }

    @Override
    public boolean supportsParameter(MethodParameter p) {
        return p.getParameterType().equals(String.class)
                && (p.hasParameterAnnotation(CustomerId.class) || p.hasParameterAnnotation(CustomerEmail.class));
    }

    @Override
    public Object resolveArgument(MethodParameter p, ModelAndViewContainer m, NativeWebRequest request,
                                  WebDataBinderFactory f) {
        boolean wantsId = p.hasParameterAnnotation(CustomerId.class);
        if (securityEnabled) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken token) {
                Jwt jwt = token.getToken();
                return wantsId ? jwt.getSubject() : jwt.getClaimAsString("email");
            }
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                    "A valid access token is required.");
        }
        String header = request.getHeader(wantsId ? "X-Customer-Id" : "X-Customer-Email");
        if (wantsId && (header == null || header.isBlank())) {
            throw ApiException.badRequest("MISSING_CUSTOMER_ID",
                    "Header X-Customer-Id is required while security is disabled.");
        }
        return header;
    }
}
