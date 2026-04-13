package com.zerionis.log.spring.filter;

import com.zerionis.log.core.context.ZerionisContext;
import com.zerionis.log.core.format.JsonFieldNames;
import com.zerionis.log.core.model.EventType;
import com.zerionis.log.core.model.ZerionisLogEvent;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /** Content types that are never logged (binary, multipart, file uploads). */
    private static final List<String> SKIP_CONTENT_TYPES = List.of(
            "multipart/",
            "application/octet-stream",
            "image/",
            "audio/",
            "video/",
            "application/pdf",
            "application/zip",
            "application/gzip"
    );

    // Cached reflection handles for Spring Security userId extraction
    private static volatile boolean securityReflectionResolved = false;
    private static Class<?> cachedContextHolderClass;
    private static java.lang.reflect.Method cachedGetContextMethod;
    private static java.lang.reflect.Method cachedGetAuthenticationMethod;
    private static java.lang.reflect.Method cachedIsAuthenticatedMethod;
    private static java.lang.reflect.Method cachedGetNameMethod;

    private final TraceIdGenerator traceIdGenerator;
    private final RequestIdGenerator requestIdGenerator;
    private final ZerionisProperties properties;
    private final LogSanitizer sanitizer;

    public ZerionisRequestFilter(TraceIdGenerator traceIdGenerator,
                                  RequestIdGenerator requestIdGenerator,
                                  ZerionisProperties properties,
                                  LogSanitizer sanitizer) {
        this.traceIdGenerator = traceIdGenerator;
        this.requestIdGenerator = requestIdGenerator;
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

        // Wrap request/response for body capture if enabled and content type is allowed
        HttpServletRequest httpRequest = request;
        HttpServletResponse httpResponse = response;
        String capturedRequestBody = null;
        String capturedResponseBody = null;

        boolean captureRequestBody = properties.isIncludeRequestBody()
                && isBodyContentTypeAllowed(request.getContentType());
        boolean captureResponseBody = properties.isIncludeResponseBody();

        if (captureRequestBody) {
            org.springframework.web.util.ContentCachingRequestWrapper wrappedRequest =
                    new org.springframework.web.util.ContentCachingRequestWrapper(httpRequest, 10 * 1024);
            httpRequest = wrappedRequest;
        }

        org.springframework.web.util.ContentCachingResponseWrapper wrappedResponse = null;
        if (captureResponseBody) {
            wrappedResponse = new org.springframework.web.util.ContentCachingResponseWrapper(httpResponse);
            httpResponse = wrappedResponse;
        }

        // Capture and redact request headers (only when enabled)
        Map<String, String> sanitizedHeaders = properties.isIncludeHeaders()
                ? captureHeaders(request) : Collections.emptyMap();

        // Emit REQUEST_START if enabled
        if (properties.isRequestStartEnabled()) {
            emitEvent(EventType.REQUEST_START, path, httpMethod, clientIp,
                      traceId, requestId, null, null, null, null, null, sanitizedHeaders);
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
            if (captureRequestBody && httpRequest instanceof org.springframework.web.util.ContentCachingRequestWrapper) {
                byte[] body = ((org.springframework.web.util.ContentCachingRequestWrapper) httpRequest).getContentAsByteArray();
                if (body.length > 0) {
                    String rawBody = InputValidator.truncateBody(new String(body, StandardCharsets.UTF_8));
                    capturedRequestBody = sanitizer.sanitizeJsonBody(rawBody);
                }
            }
            if (wrappedResponse != null) {
                byte[] body = wrappedResponse.getContentAsByteArray();
                if (body.length > 0) {
                    String responseContentType = wrappedResponse.getContentType();
                    if (isBodyContentTypeAllowed(responseContentType)) {
                        String rawBody = InputValidator.truncateBody(new String(body, StandardCharsets.UTF_8));
                        capturedResponseBody = sanitizer.sanitizeJsonBody(rawBody);
                    }
                }
                // Copy body back to the actual response so the client receives it
                try {
                    wrappedResponse.copyBodyToResponse();
                } catch (IOException copyError) {
                    log.debug("Failed to copy response body: {}", copyError.getMessage());
                }
            }

            // Extract userId after filter chain — Spring Security is now populated
            String userId = extractUserId();
            if (userId != null) {
                MDC.put(JsonFieldNames.MDC_USER_ID, userId);
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
                      capturedRequestBody, capturedResponseBody, sanitizedHeaders);

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
                           String requestBody, String responseBody,
                           Map<String, String> headers) {

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

        // Include sanitized headers as extra fields (after context extras so they don't get overwritten)
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                builder.addExtra("header." + header.getKey(), header.getValue());
            }
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
     * Captures request headers with sensitive values redacted.
     */
    private Map<String, String> captureHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames == null) {
            return headers;
        }
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            String value = request.getHeader(name);
            headers.put(name, sanitizer.sanitizeHeader(name, value));
        }
        return headers;
    }

    /**
     * Checks if the given content type is allowed for body logging.
     * Returns true if no content-type restrictions are configured (empty list),
     * or if the content type matches one of the allowed types.
     * Always returns false for binary/multipart content types.
     */
    private boolean isBodyContentTypeAllowed(String contentType) {
        if (contentType == null) {
            return false;
        }

        String lowerContentType = contentType.toLowerCase();

        // Always skip binary and multipart content
        for (String skipType : SKIP_CONTENT_TYPES) {
            if (lowerContentType.startsWith(skipType)) {
                return false;
            }
        }

        List<String> allowedTypes = properties.getBodyContentTypes();

        // Empty list means allow all (non-binary) content types
        if (allowedTypes == null || allowedTypes.isEmpty()) {
            return true;
        }

        // Check if content type matches any allowed type
        for (String allowed : allowedTypes) {
            if (lowerContentType.startsWith(allowed.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Extracts the authenticated user ID from Spring Security's SecurityContext.
     * Returns null if Spring Security is not on the classpath or no user is authenticated.
     * Caches Class.forName and Method lookups to avoid repeated reflection overhead.
     */
    private String extractUserId() {
        try {
            if (!securityReflectionResolved) {
                synchronized (ZerionisRequestFilter.class) {
                    if (!securityReflectionResolved) {
                        try {
                            cachedContextHolderClass = Class.forName(
                                    "org.springframework.security.core.context.SecurityContextHolder");
                            cachedGetContextMethod = cachedContextHolderClass.getMethod("getContext");
                        } catch (ClassNotFoundException e) {
                            // Spring Security not on classpath
                        }
                        securityReflectionResolved = true;
                    }
                }
            }

            if (cachedContextHolderClass == null || cachedGetContextMethod == null) {
                return null;
            }

            Object context = cachedGetContextMethod.invoke(null);
            if (context == null) return null;

            // Lazily cache SecurityContext/Authentication method handles
            if (cachedGetAuthenticationMethod == null) {
                synchronized (ZerionisRequestFilter.class) {
                    if (cachedGetAuthenticationMethod == null) {
                        cachedGetAuthenticationMethod = context.getClass().getMethod("getAuthentication");
                        // Get methods from Authentication interface directly
                        Class<?> authClass = cachedGetAuthenticationMethod.getReturnType();
                        cachedIsAuthenticatedMethod = authClass.getMethod("isAuthenticated");
                        cachedGetNameMethod = authClass.getMethod("getName");
                    }
                }
            }

            Object authentication = cachedGetAuthenticationMethod.invoke(context);
            if (authentication == null) return null;

            Object isAuthenticated = cachedIsAuthenticatedMethod.invoke(authentication);
            if (!Boolean.TRUE.equals(isAuthenticated)) return null;

            Object principal = cachedGetNameMethod.invoke(authentication);
            if (principal == null || "anonymousUser".equals(principal.toString())) return null;

            return principal.toString();
        } catch (Exception e) {
            // Spring Security error — skip userId
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
     * Supports prefix matching: if an excluded endpoint ends with {@code *},
     * any path starting with that prefix is excluded.
     */
    private boolean isExcluded(String path) {
        List<String> excluded = properties.getExcludeEndpoints();
        if (excluded == null || excluded.isEmpty()) {
            return false;
        }
        for (String endpoint : excluded) {
            if (endpoint.endsWith("*")) {
                if (path.startsWith(endpoint.substring(0, endpoint.length() - 1))) {
                    return true;
                }
            } else if (endpoint.equals(path)) {
                return true;
            }
        }
        return false;
    }
}
