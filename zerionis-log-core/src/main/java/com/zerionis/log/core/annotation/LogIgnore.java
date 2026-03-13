package com.zerionis.log.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller class or method to be completely ignored by the Zerionis aspect.
 * No METHOD_SLOW or METHOD_ERROR events will be emitted for annotated methods.
 *
 * <p>When placed on a class, all methods in that class are ignored.</p>
 * <p>When placed on a method, only that method is ignored.</p>
 *
 * <p>Note: the request filter still emits REQUEST_END/REQUEST_ERROR events.
 * To exclude endpoints from request logging, use {@code zerionis.log.exclude-endpoints}.</p>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface LogIgnore {
}
