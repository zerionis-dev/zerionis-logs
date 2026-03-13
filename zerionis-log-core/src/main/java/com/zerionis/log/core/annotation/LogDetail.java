package com.zerionis.log.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Controls the detail level for a specific controller method.
 * Overrides global configuration for request/response body capture.
 *
 * <p>Example usage:</p>
 * <pre>
 *   &#64;PostMapping("/payments/charge")
 *   &#64;LogDetail(includeRequestBody = true, includeResponseBody = false)
 *   public PaymentResult charge(&#64;RequestBody ChargeRequest req) { ... }
 * </pre>
 *
 * <p>Note: body capture must be enabled globally ({@code zerionis.log.include-request-body=true})
 * for this annotation to take effect. This annotation can only restrict, not enable body capture
 * beyond the global setting — except when used to enable it per-method.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogDetail {

    /** Whether to include the HTTP request body in log events. Default: true. */
    boolean includeRequestBody() default true;

    /** Whether to include the HTTP response body in log events. Default: true. */
    boolean includeResponseBody() default true;
}
