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

        String userId = extractUserId();
        if (userId != null) {
            MDC.put(JsonFieldNames.MDC_USER_ID, userId);
        }

        String capturedRequestBody = null;
        String capturedResponseBody = null;

        HttpServletRequest activeRequest = request;
        HttpServletResponse activeResponse = response;

        if (properties.isIncludeRequestBody()) {
            org.springframework.web.util.ContentCachingRequestWrapper wrappedRequest =
                    new org.springframework.web.util.ContentCachingRequestWrapper(request);
            activeRequest = wrappedRequest;
        }

        org.springframework.web.util.ContentCachingResponseWrapper wrappedResponse = null;
        if (properties.isIncludeResponseBody()) {
            wrappedResponse = new org.springframework.web.util.ContentCachingResponseWrapper(response);
            activeResponse = wrappedResponse;
        }

        if (properties.isRequestStartEnabled()) {
            emitEvent(EventType.REQUEST_START, path, httpMethod, clientIp,
                      traceId, requestId, null, null, null, null, null);
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

            if (properties.isIncludeRequestBody() && activeRequest instanceof org.springframework.web.util.ContentCachingRequestWrapper) {
                byte[] body = ((org.springframework.web.util.ContentCachingRequestWrapper) activeRequest).getContentAsByteArray();
                if (body.length > 0) {
                    capturedRequestBody = InputValidator.truncateBody(new String(body, StandardCharsets.UTF_8));
                }
            }
            if (wrappedResponse != null) {
                byte[] body = wrappedResponse.getContentAsByteArray();
                if (body.length > 0) {
                    capturedResponseBody = InputValidator.truncateBody(new String(body, StandardCharsets.UTF_8));
                }
                wrappedResponse.copyBodyToResponse();
            }

            EventType eventType = (caughtException != null || httpStatus >= 500)
                    ? EventType.REQUEST_ERROR
                    : EventType.REQUEST_END;

            emitEvent(eventType, path, httpMethod, clientIp,
                      traceId, requestId, httpStatus, durationMs, caughtException,
                      capturedRequestBody, capturedResponseBody);

            MDC.clear();
            ZerionisContext.clear();
        }
    }

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
        Map<String, Object> extra = ZerionisContext.getAll();
        if (!extra.isEmpty()) {
            builder.extra(sanitizer.sanitize(extra));
        }

        ZerionisLogEvent event = builder.build();

        if (eventType == EventType.REQUEST_ERROR) {
            log.error("{}", event);
        } else {
            log.info("{}", event);
        }
    }

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

    private boolean isExcluded(String path) {
        List<String> excluded = properties.getExcludeEndpoints();
        if (excluded == null || excluded.isEmpty()) {
            return false;
        }
        return excluded.contains(path);
    }
}
