package com.zerionis.log.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method to skip aspect-level tracking.
 * No METHOD_SLOW or METHOD_ERROR events will be emitted.
 *
 * <p>Unlike {@link LogIgnore}, this can only be placed on methods (not classes).
 * Use this for individual endpoints that don't need method-level monitoring,
 * such as health checks or simple status endpoints.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogSkip {
}
