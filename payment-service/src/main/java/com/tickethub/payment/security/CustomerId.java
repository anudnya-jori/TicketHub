package com.tickethub.payment.security;

import java.lang.annotation.*;

/** Controller parameter: the authenticated customer's id (token "sub"; the X-Customer-Id header only when security is off). */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CustomerId {
}
