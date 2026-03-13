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
 * <p>Spring Boot 2.7.x version. Functionally identical to the Spring Boot 3 version.</p>
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

    @Around("within(@org.springframework.web.bind.annotation.RestController *)")
    public Object interceptController(ProceedingJoinPoint joinPoint) throws Throwable {
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

            if (durationMs >= properties.getSlowMethodThresholdMs()) {
                emitSlowMethodEvent(className, methodName, durationMs);
            }
            return result;

        } catch (Throwable throwable) {
            long durationMs = System.currentTimeMillis() - startTime;
            emitMethodErrorEvent(className, methodName, durationMs, throwable);
            throw throwable;
        }
    }

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
