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
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Servlet Filter that captures HTTP request context and emits structured log events.
 *
 * <p>Spring Boot 2.7.x version using {@code javax.servlet}.
 * Functionally identical to the Spring Boot 3 version.</p>
 *
 * @see com.zerionis.log.core.model.EventType
 */
public class ZerionisRequestFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(ZerionisRequestFilter.class);
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String ERROR_ATTRIBUTE = "zerionis.error";

    /** Content types that are never logged (binary, multipart, file uploads). */
    private static final List<String> SKIP_CONTENT_TYPES = Arrays.asList(
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

        if (isExcluded(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Validate trace ID from header: only accept safe characters.
        // If invalid (scripts, injection attempts, too long), discard and generate new.
        String traceId = InputValidator.validateTraceId(request.getHeader(TRACE_ID_HEADER));
        if (traceId == null) {
            traceId = traceIdGenerator.generate();
        }

        String requestId = requestIdGenerator.generate();
        String httpMethod = request.getMethod();
        // Validate client IP to prevent arbitrary string injection via X-Forwarded-For
        String clientIp = InputValidator.validateClientIp(getClientIp(request));

        MDC.put(JsonFieldNames.MDC_TRACE_ID, traceId);
        MDC.put(JsonFieldNames.MDC_REQUEST_ID, requestId);
        MDC.put(JsonFieldNames.MDC_PATH, path);
        MDC.put(JsonFieldNames.MDC_HTTP_METHOD, httpMethod);
        MDC.put(JsonFieldNames.MDC_CLIENT_IP, clientIp);

        String capturedRequestBody = null;
        String capturedResponseBody = null;

        HttpServletRequest activeRequest = request;
        HttpServletResponse activeResponse = response;

        boolean captureRequestBody = properties.isIncludeRequestBody()
                && isBodyContentTypeAllowed(request.getContentType());
        boolean captureResponseBody = properties.isIncludeResponseBody();

        if (captureRequestBody) {
            org.springframework.web.util.ContentCachingRequestWrapper wrappedRequest =
                    new org.springframework.web.util.ContentCachingRequestWrapper(request);
            activeRequest = wrappedRequest;
        }

        org.springframework.web.util.ContentCachingResponseWrapper wrappedResponse = null;
        if (captureResponseBody) {
            wrappedResponse = new org.springframework.web.util.ContentCachingResponseWrapper(response);
            activeResponse = wrappedResponse;
        }

        // Capture and redact request headers (only when enabled)
        Map<String, String> sanitizedHeaders = properties.isIncludeHeaders()
                ? captureHeaders(request) : Collections.emptyMap();

        if (properties.isRequestStartEnabled()) {
            emitEvent(EventType.REQUEST_START, path, httpMethod, clientIp,
                      traceId, requestId, null, null, null, null, null, sanitizedHeaders);
        }

        long startTime = System.currentTimeMillis();
        Throwable caughtException = null;

        try {
            chain.doFilter(activeRequest, activeResponse);
        } catch (Exception e) {
            caughtException = e;
            throw e;
        } finally {
            long durationMs = System.currentTimeMillis() - startTime;
            int httpStatus = ((HttpServletResponse) activeResponse).getStatus();

            if (caughtException == null) {
                Object errorAttr = activeRequest.getAttribute(ERROR_ATTRIBUTE);
                if (errorAttr instanceof Throwable) {
                    caughtException = (Throwable) errorAttr;
                }
            }

            if (captureRequestBody && activeRequest instanceof org.springframework.web.util.ContentCachingRequestWrapper) {
                byte[] body = ((org.springframework.web.util.ContentCachingRequestWrapper) activeRequest).getContentAsByteArray();
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

            EventType eventType = (caughtException != null || httpStatus >= 500)
                    ? EventType.REQUEST_ERROR
                    : EventType.REQUEST_END;

            emitEvent(eventType, path, httpMethod, clientIp,
                      traceId, requestId, httpStatus, durationMs, caughtException,
                      capturedRequestBody, capturedResponseBody, sanitizedHeaders);

            MDC.clear();
            ZerionisContext.clear();
        }
    }

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
        Map<String, Object> extra = ZerionisContext.getAll();
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

        if (eventType == EventType.REQUEST_ERROR) {
            log.error("{}", event);
        } else {
            log.info("{}", event);
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
            return null;
        }
    }

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

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
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
