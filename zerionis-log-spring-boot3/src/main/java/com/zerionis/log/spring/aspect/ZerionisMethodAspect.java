package com.zerionis.log.spring.aspect;

import com.zerionis.log.core.annotation.LogIgnore;
import com.zerionis.log.core.annotation.LogSkip;
import com.zerionis.log.core.format.JsonFieldNames;
import com.zerionis.log.core.format.ZerionisLogFormatter;
import com.zerionis.log.core.model.EventType;
import com.zerionis.log.core.model.ZerionisLogEvent;
import com.zerionis.log.core.util.StackTraceUtils;
import com.zerionis.log.spring.config.ZerionisProperties;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.lang.reflect.Method;
import java.time.Instant;

/**
 * AOP aspect that intercepts controller methods to detect slow execution and errors.
 *
 * <p>Emits two types of events:</p>
 * <ul>
 *   <li>{@code METHOD_SLOW} — when a method exceeds the configured duration threshold</li>
 *   <li>{@code METHOD_ERROR} — when a method throws an exception</li>
 * </ul>
 *
 * <p>By default, only intercepts classes annotated with {@code @RestController}.
 * Does not intercept all @Service or @Repository methods to avoid log noise.</p>
 */
@Aspect
public class ZerionisMethodAspect {

    private static final Logger log = LoggerFactory.getLogger(ZerionisMethodAspect.class);

    private final ZerionisLogFormatter formatter;
    private final ZerionisProperties properties;

    public ZerionisMethodAspect(ZerionisLogFormatter formatter, ZerionisProperties properties) {
        this.formatter = formatter;
        this.properties = properties;
    }

    /**
     * Intercepts all public methods in classes annotated with @RestController.
     * Measures execution time and catches exceptions.
     */
    @Around("within(@org.springframework.web.bind.annotation.RestController *)")
    public Object interceptController(ProceedingJoinPoint joinPoint) throws Throwable {
        // Check if method or class is annotated with @LogIgnore or @LogSkip
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        if (method.isAnnotationPresent(LogIgnore.class) ||
            method.isAnnotationPresent(LogSkip.class) ||
            joinPoint.getTarget().getClass().isAnnotationPresent(LogIgnore.class)) {
            return joinPoint.proceed();
        }

        String className = joinPoint.getTarget().getClass().getName();
        String methodName = joinPoint.getSignature().getName();

        long startTime = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();

            long durationMs = System.currentTimeMillis() - startTime;

            // Emit METHOD_SLOW if execution exceeded the threshold
            if (durationMs >= properties.getSlowMethodThresholdMs()) {
                emitSlowMethodEvent(className, methodName, durationMs);
            }

            return result;

        } catch (Throwable throwable) {
            long durationMs = System.currentTimeMillis() - startTime;

            // Emit METHOD_ERROR for exceptions in controller methods
            emitMethodErrorEvent(className, methodName, durationMs, throwable);

            // Re-throw so Spring can handle the exception normally
            throw throwable;
        }
    }

    /**
     * Emits a METHOD_SLOW event when a method exceeds the duration threshold.
     */
    private void emitSlowMethodEvent(String className, String methodName, long durationMs) {
        ZerionisLogEvent event = ZerionisLogEvent.builder()
                .timestamp(Instant.now())
                .level("WARN")
                .eventType(EventType.METHOD_SLOW)
                .service(properties.getServiceName())
                .environment(properties.getEnvironment())
                .version(properties.getVersion())
                .traceId(MDC.get(JsonFieldNames.MDC_TRACE_ID))
                .requestId(MDC.get(JsonFieldNames.MDC_REQUEST_ID))
                .path(MDC.get(JsonFieldNames.MDC_PATH))
                .httpMethod(MDC.get(JsonFieldNames.MDC_HTTP_METHOD))
                .className(className)
                .methodName(methodName)
                .durationMs(durationMs)
                .message("Slow method: " + className + "." + methodName + " took " + durationMs + "ms")
                .build();

        log.warn("{}", event);
    }

    /**
     * Emits a METHOD_ERROR event when a method throws an exception.
     */
    private void emitMethodErrorEvent(String className, String methodName,
                                       long durationMs, Throwable throwable) {
        ZerionisLogEvent event = ZerionisLogEvent.builder()
                .timestamp(Instant.now())
                .level("ERROR")
                .eventType(EventType.METHOD_ERROR)
                .service(properties.getServiceName())
                .environment(properties.getEnvironment())
                .version(properties.getVersion())
                .traceId(MDC.get(JsonFieldNames.MDC_TRACE_ID))
                .requestId(MDC.get(JsonFieldNames.MDC_REQUEST_ID))
                .path(MDC.get(JsonFieldNames.MDC_PATH))
                .httpMethod(MDC.get(JsonFieldNames.MDC_HTTP_METHOD))
                .className(className)
                .methodName(methodName)
                .durationMs(durationMs)
                .message("Error in " + className + "." + methodName + ": " + throwable.getMessage())
                .error(StackTraceUtils.fromThrowable(throwable, properties.getMaxStacktraceLines()))
                .build();

        log.error("{}", event);
    }
}
