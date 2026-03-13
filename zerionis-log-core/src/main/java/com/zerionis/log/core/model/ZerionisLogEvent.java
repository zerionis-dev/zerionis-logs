package com.zerionis.log.core.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Main structured log event model.
 *
 * This is the core log event model for zerionis-log.
 * Every instance represents something that happened in the application:
 * an HTTP request, an error, a slow method, etc.
 *
 * All field names use camelCase. Null fields are excluded from the JSON output.
 *
 * @see EventType for the list of event types
 * @see ZerionisError for error detail structure
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ZerionisLogEvent {

    /** Exact moment of the event in UTC (ISO 8601 with milliseconds). */
    @JsonFormat(shape = JsonFormat.Shape.STRING, timezone = "UTC")
    private Instant timestamp;

    /** Log level: TRACE, DEBUG, INFO, WARN, ERROR. */
    private String level;

    /** Event classification. Used to detect anomalies and group incidents. */
    private EventType eventType;

    /** Name of the service that emitted the log (e.g. "payments-api"). */
    private String service;

    /** Deployment environment: production, staging, development. */
    private String environment;

    /** Service version. Useful for correlating incidents with deploys. */
    private String version;

    /**
     * Distributed trace identifier (UUID v4).
     * Shared across services to reconstruct the full request chain.
     * If the incoming request carries an X-Trace-Id header, that value is reused.
     */
    private String traceId;

    /**
     * Identifier for this specific HTTP request within the service.
     * Different from traceId: requestId is local, traceId is distributed.
     * Format: "req-" + 8-char UUID segment.
     */
    private String requestId;

    /** Request path without query parameters (e.g. "/payments/charge"). */
    private String path;

    /** HTTP method: GET, POST, PUT, DELETE, PATCH. */
    private String httpMethod;

    /** HTTP response status code. Null for REQUEST_START events. */
    private Integer httpStatus;

    /** Client IP address. */
    private String clientIp;

    /** Authenticated user ID. Null if the request doesn't require auth. */
    private String userId;

    /** Fully qualified class name (e.g. "com.example.PaymentService"). */
    private String className;

    /** Method name (e.g. "processCharge"). */
    private String methodName;

    /** Duration in milliseconds. Null when not applicable. */
    private Long durationMs;

    /** Human-readable description of the event. */
    private String message;

    /** HTTP request body. Only present when body capture is enabled. */
    private String requestBody;

    /** HTTP response body. Only present when body capture is enabled. */
    private String responseBody;

    /** Error detail. Only present when an exception occurred. */
    private ZerionisError error;

    /**
     * Developer-defined extra fields.
     * Populated via {@link com.zerionis.log.core.context.ZerionisContext#put(String, Object)}.
     * Limited to a configurable max number of fields (default: 20).
     * Sensitive fields are automatically redacted.
     */
    private Map<String, Object> extra;

    public ZerionisLogEvent() {
    }

    /** Creates a new builder for fluent event construction. */
    public static Builder builder() {
        return new Builder();
    }

    // ── Getters and Setters ──

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public ZerionisError getError() {
        return error;
    }

    public void setError(ZerionisError error) {
        this.error = error;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }

    public void setExtra(Map<String, Object> extra) {
        this.extra = extra;
    }

    // ── Builder ──

    /**
     * Fluent builder for constructing {@link ZerionisLogEvent} instances.
     * If no timestamp is set, defaults to the current time (UTC).
     */
    public static class Builder {

        private final ZerionisLogEvent event = new ZerionisLogEvent();

        public Builder timestamp(Instant timestamp) {
            event.setTimestamp(timestamp);
            return this;
        }

        public Builder level(String level) {
            event.setLevel(level);
            return this;
        }

        public Builder eventType(EventType eventType) {
            event.setEventType(eventType);
            return this;
        }

        public Builder service(String service) {
            event.setService(service);
            return this;
        }

        public Builder environment(String environment) {
            event.setEnvironment(environment);
            return this;
        }

        public Builder version(String version) {
            event.setVersion(version);
            return this;
        }

        public Builder traceId(String traceId) {
            event.setTraceId(traceId);
            return this;
        }

        public Builder requestId(String requestId) {
            event.setRequestId(requestId);
            return this;
        }

        public Builder path(String path) {
            event.setPath(path);
            return this;
        }

        public Builder httpMethod(String httpMethod) {
            event.setHttpMethod(httpMethod);
            return this;
        }

        public Builder httpStatus(Integer httpStatus) {
            event.setHttpStatus(httpStatus);
            return this;
        }

        public Builder clientIp(String clientIp) {
            event.setClientIp(clientIp);
            return this;
        }

        public Builder userId(String userId) {
            event.setUserId(userId);
            return this;
        }

        public Builder className(String className) {
            event.setClassName(className);
            return this;
        }

        public Builder methodName(String methodName) {
            event.setMethodName(methodName);
            return this;
        }

        public Builder durationMs(Long durationMs) {
            event.setDurationMs(durationMs);
            return this;
        }

        public Builder message(String message) {
            event.setMessage(message);
            return this;
        }

        public Builder requestBody(String requestBody) {
            event.setRequestBody(requestBody);
            return this;
        }

        public Builder responseBody(String responseBody) {
            event.setResponseBody(responseBody);
            return this;
        }

        public Builder error(ZerionisError error) {
            event.setError(error);
            return this;
        }

        public Builder extra(Map<String, Object> extra) {
            event.setExtra(extra);
            return this;
        }

        /** Adds a single extra field. Creates the map if it doesn't exist yet. */
        public Builder addExtra(String key, Object value) {
            if (event.getExtra() == null) {
                event.setExtra(new HashMap<>());
            }
            event.getExtra().put(key, value);
            return this;
        }

        /** Builds the event. Defaults timestamp to now (UTC) if not set. */
        public ZerionisLogEvent build() {
            if (event.getTimestamp() == null) {
                event.setTimestamp(Instant.now());
            }
            return event;
        }
    }
}
