package com.tickethub.booking.security;

import java.lang.annotation.*;

/** Controller parameter: the customer's e-mail (token "email" claim; X-Customer-Email header when security is off). May be null. */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CustomerEmail {
}
