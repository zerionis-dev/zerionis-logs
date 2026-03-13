package com.zerionis.log.spring.filter;

import com.zerionis.log.core.context.ZerionisContext;
import com.zerionis.log.core.format.JsonFieldNames;
import com.zerionis.log.core.model.EventType;
import com.zerionis.log.core.model.ZerionisLogEvent;
import com.zerionis.log.core.format.ZerionisLogFormatter;
import com.zerionis.log.core.util.InputValidator;
import com.zerionis.log.core.util.LogSanitizer;
import com.zerionis.log.core.util.RequestIdGenerator;
import com.zerionis.log.core.util.StackTraceUtils;
import com.zerionis.log.core.util.TraceIdGenerator;
import com.zerionis.log.spring.config.ZerionisProperties;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Servlet Filter that captures HTTP request context and emits structured log events.
 *
 * <p>For every incoming HTTP request, this filter:</p>
 * <ol>
 *   <li>Generates or reuses a traceId (from X-Trace-Id header)</li>
 *   <li>Generates a requestId</li>
 *   <li>Populates MDC with request context (traceId, path, method, IP)</li>
 *   <li>Measures request duration</li>
 *   <li>Emits a REQUEST_END or REQUEST_ERROR event</li>
 *   <li>Cleans up MDC and ZerionisContext</li>
 * </ol>
 *
 * <p>This is the only component that emits REQUEST_END and REQUEST_ERROR events.
 * The ControllerAdvice enriches error context but does not emit its own log event,
 * preventing duplicate logs.</p>
 */
public class ZerionisRequestFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(ZerionisRequestFilter.class);

    /** Header name for distributed trace ID propagation. */
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** Request attribute used by ControllerAdvice to pass exception info to this filter. */
    public static final String ERROR_ATTRIBUTE = "zerionis.error";

    private final TraceIdGenerator traceIdGenerator;
    private final RequestIdGenerator requestIdGenerator;
    private final ZerionisLogFormatter formatter;
    private final ZerionisProperties properties;
    private final LogSanitizer sanitizer;

    public ZerionisRequestFilter(TraceIdGenerator traceIdGenerator,
                                  RequestIdGenerator requestIdGenerator,
                                  ZerionisLogFormatter formatter,
                                  ZerionisProperties properties,
                                  LogSanitizer sanitizer) {
        this.traceIdGenerator = traceIdGenerator;
        this.requestIdGenerator = requestIdGenerator;
        this.formatter = formatter;
        this.properties = properties;
        this.sanitizer = sanitizer;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain chain) throws IOException, ServletException {

        if (!(servletRequest instanceof HttpServletRequest) ||
            !(servletResponse instanceof HttpServletResponse)) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String path = InputValidator.truncatePath(request.getRequestURI());

        // Skip excluded endpoints (e.g. /actuator/health, /favicon.ico)
        if (isExcluded(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Validate trace ID from header: only accept safe characters (alphanumeric, hyphens, dots).
        // If the header contains scripts, injection attempts, or is too long, discard and generate new.
        String traceId = InputValidator.validateTraceId(request.getHeader(TRACE_ID_HEADER));
        if (traceId == null) {
            traceId = traceIdGenerator.generate();
        }

        String requestId = requestIdGenerator.generate();
        String httpMethod = request.getMethod();
        // Validate client IP to prevent arbitrary string injection via X-Forwarded-For
        String clientIp = InputValidator.validateClientIp(getClientIp(request));

        // Populate MDC so all log statements within this request include context
        MDC.put(JsonFieldNames.MDC_TRACE_ID, traceId);
        MDC.put(JsonFieldNames.MDC_REQUEST_ID, requestId);
        MDC.put(JsonFieldNames.MDC_PATH, path);
        MDC.put(JsonFieldNames.MDC_HTTP_METHOD, httpMethod);
        MDC.put(JsonFieldNames.MDC_CLIENT_IP, clientIp);

        // Extract userId from Spring Security if available
        String userId = extractUserId();
        if (userId != null) {
            MDC.put(JsonFieldNames.MDC_USER_ID, userId);
        }

        // Wrap request/response for body capture if enabled
        HttpServletRequest httpRequest = request;
        HttpServletResponse httpResponse = response;
        String capturedRequestBody = null;
        String capturedResponseBody = null;

        if (properties.isIncludeRequestBody()) {
            org.springframework.web.util.ContentCachingRequestWrapper wrappedRequest =
                    new org.springframework.web.util.ContentCachingRequestWrapper(httpRequest);
            httpRequest = wrappedRequest;
        }

        org.springframework.web.util.ContentCachingResponseWrapper wrappedResponse = null;
        if (properties.isIncludeResponseBody()) {
            wrappedResponse = new org.springframework.web.util.ContentCachingResponseWrapper(httpResponse);
            httpResponse = wrappedResponse;
        }

        // Emit REQUEST_START if enabled
        if (properties.isRequestStartEnabled()) {
            emitEvent(EventType.REQUEST_START, path, httpMethod, clientIp,
                      traceId, requestId, null, null, null, null, null);
        }

        long startTime = System.currentTimeMillis();
        Throwable caughtException = null;

        try {
            chain.doFilter(httpRequest, httpResponse);
        } catch (Exception e) {
            caughtException = e;
            throw e;
        } finally {
            long durationMs = System.currentTimeMillis() - startTime;
            int httpStatus = httpResponse.getStatus();

            // Check if ControllerAdvice stored an exception in request attributes
            if (caughtException == null) {
                Object errorAttr = httpRequest.getAttribute(ERROR_ATTRIBUTE);
                if (errorAttr instanceof Throwable) {
                    caughtException = (Throwable) errorAttr;
                }
            }

            // Capture bodies if enabled
            if (properties.isIncludeRequestBody() && httpRequest instanceof org.springframework.web.util.ContentCachingRequestWrapper) {
                byte[] body = ((org.springframework.web.util.ContentCachingRequestWrapper) httpRequest).getContentAsByteArray();
                if (body.length > 0) {
                    capturedRequestBody = InputValidator.truncateBody(new String(body, java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            if (wrappedResponse != null) {
                byte[] body = wrappedResponse.getContentAsByteArray();
                if (body.length > 0) {
                    capturedResponseBody = InputValidator.truncateBody(new String(body, java.nio.charset.StandardCharsets.UTF_8));
                }
                // Copy body back to the actual response so the client receives it
                wrappedResponse.copyBodyToResponse();
            }

            // Determine event type based on whether an error occurred
            EventType eventType;
            if (caughtException != null || httpStatus >= 500) {
                eventType = EventType.REQUEST_ERROR;
            } else {
                eventType = EventType.REQUEST_END;
            }

            emitEvent(eventType, path, httpMethod, clientIp,
                      traceId, requestId, httpStatus, durationMs, caughtException,
                      capturedRequestBody, capturedResponseBody);

            // Clean up to prevent memory leaks in thread pools
            MDC.clear();
            ZerionisContext.clear();
        }
    }

    /**
     * Emits a structured log event for the current request.
     */
    private void emitEvent(EventType eventType, String path, String httpMethod,
                           String clientIp, String traceId, String requestId,
                           Integer httpStatus, Long durationMs, Throwable exception,
                           String requestBody, String responseBody) {

        ZerionisLogEvent.Builder builder = ZerionisLogEvent.builder()
                .timestamp(Instant.now())
                .level(eventType == EventType.REQUEST_ERROR ? "ERROR" : "INFO")
                .eventType(eventType)
                .service(properties.getServiceName())
                .environment(properties.getEnvironment())
                .version(properties.getVersion())
                .traceId(traceId)
                .requestId(requestId)
                .path(path)
                .httpMethod(httpMethod)
                .httpStatus(httpStatus)
                .clientIp(clientIp)
                .durationMs(durationMs)
                .message(buildMessage(eventType, httpMethod, path, httpStatus, durationMs));

        if (requestBody != null) {
            builder.requestBody(requestBody);
        }
        if (responseBody != null) {
            builder.responseBody(responseBody);
        }

        if (exception != null) {
            builder.error(StackTraceUtils.fromThrowable(exception, properties.getMaxStacktraceLines()));
        }

        // Include extra fields from ZerionisContext, with sensitive values redacted
        var extra = ZerionisContext.getAll();
        if (!extra.isEmpty()) {
            builder.extra(sanitizer.sanitize(extra));
        }

        ZerionisLogEvent event = builder.build();

        // Pass the pre-built event as argument so ZerionisJsonLayout serializes it directly
        if (eventType == EventType.REQUEST_ERROR) {
            log.error("{}", event);
        } else {
            log.info("{}", event);
        }
    }

    /**
     * Builds a human-readable message for the log event.
     */
    private String buildMessage(EventType eventType, String httpMethod, String path,
                                Integer httpStatus, Long durationMs) {
        switch (eventType) {
            case REQUEST_START:
                return httpMethod + " " + path + " started";
            case REQUEST_END:
                return httpMethod + " " + path + " completed " + httpStatus + " in " + durationMs + "ms";
            case REQUEST_ERROR:
                return httpMethod + " " + path + " failed " + httpStatus + " in " + durationMs + "ms";
            default:
                return httpMethod + " " + path;
        }
    }

    /**
     * Extracts the authenticated user ID from Spring Security's SecurityContext.
     * Returns null if Spring Security is not on the classpath or no user is authenticated.
     */
    private String extractUserId() {
        try {
            Class<?> contextHolderClass = Class.forName(
                    "org.springframework.security.core.context.SecurityContextHolder");
            Object context = contextHolderClass.getMethod("getContext").invoke(null);
            if (context == null) return null;

            Object authentication = context.getClass().getMethod("getAuthentication").invoke(context);
            if (authentication == null) return null;

            Object isAuthenticated = authentication.getClass().getMethod("isAuthenticated").invoke(authentication);
            if (!Boolean.TRUE.equals(isAuthenticated)) return null;

            Object principal = authentication.getClass().getMethod("getName").invoke(authentication);
            if (principal == null || "anonymousUser".equals(principal.toString())) return null;

            return principal.toString();
        } catch (Exception e) {
            // Spring Security not on classpath or other error — skip userId
            return null;
        }
    }

    /**
     * Extracts the real client IP, considering reverse proxy headers.
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs; the first one is the original client
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Checks if the given path is in the exclude list.
     */
    private boolean isExcluded(String path) {
        List<String> excluded = properties.getExcludeEndpoints();
        if (excluded == null || excluded.isEmpty()) {
            return false;
        }
        return excluded.contains(path);
    }
}
