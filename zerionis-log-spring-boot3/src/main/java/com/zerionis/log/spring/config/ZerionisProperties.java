package com.zerionis.log.spring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for zerionis-log.
 * Bound to the {@code zerionis.log} prefix in application.yml.
 *
 * <p>All properties have sensible defaults. Zero configuration is required
 * for the library to work out of the box.</p>
 *
 * <p>Example configuration:</p>
 * <pre>
 * zerionis:
 *   log:
 *     service-name: payments-api
 *     environment: production
 *     version: 2.1.8
 * </pre>
 */
@ConfigurationProperties(prefix = "zerionis.log")
public class ZerionisProperties {

    /** Service name included in every log event. Falls back to spring.application.name. */
    private String serviceName;

    /** Deployment environment (e.g. production, staging, development). */
    private String environment;

    /** Service version. Useful for correlating incidents with deploys. */
    private String version;

    /** Whether to emit a REQUEST_START event at the beginning of each request. Default: false. */
    private boolean requestStartEnabled = false;

    /** Whether to include the HTTP request body in logs. Default: false. */
    private boolean includeRequestBody = false;

    /** Whether to include the HTTP response body in logs. Default: false. */
    private boolean includeResponseBody = false;

    /** Maximum number of stack trace lines to include in error logs. Default: 25. */
    private int maxStacktraceLines = 25;

    /** Maximum number of extra fields allowed per request via ZerionisContext. Default: 20. */
    private int maxExtraFields = 20;

    /** Method duration threshold in milliseconds for METHOD_SLOW events. Default: 1000. */
    private long slowMethodThresholdMs = 1000;

    /** Additional field names to sanitize (added to default blacklist). */
    private List<String> sanitizeFields = new ArrayList<>();

    /** Whether to include HTTP request headers in log events. Default: false. */
    private boolean includeHeaders = false;

    /** Endpoints to exclude from logging (e.g. /actuator/health). */
    private List<String> excludeEndpoints = new ArrayList<>();

    /** Whether to enable SQL query interception. Default: false. */
    private boolean sqlEnabled = false;

    /** SQL query duration threshold in milliseconds for SQL_SLOW events. Default: 500. */
    private long sqlSlowThresholdMs = 500;

    /**
     * Content types allowed for body logging. Only bodies with these content types are captured.
     * Default: only "application/json". Set to empty list to allow all content types.
     */
    private List<String> bodyContentTypes = new ArrayList<>(java.util.Collections.singletonList("application/json"));

    /**
     * Whether to enable partial redaction for long sensitive values.
     * When enabled, values longer than 12 characters show first 4 and last 4 chars (e.g. "eyJh...xYz9").
     * Short values are still fully redacted. Default: false.
     */
    private boolean partialRedactionEnabled = false;

    // ── Getters and Setters ──

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
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

    public boolean isRequestStartEnabled() {
        return requestStartEnabled;
    }

    public void setRequestStartEnabled(boolean requestStartEnabled) {
        this.requestStartEnabled = requestStartEnabled;
    }

    public boolean isIncludeRequestBody() {
        return includeRequestBody;
    }

    public void setIncludeRequestBody(boolean includeRequestBody) {
        this.includeRequestBody = includeRequestBody;
    }

    public boolean isIncludeResponseBody() {
        return includeResponseBody;
    }

    public void setIncludeResponseBody(boolean includeResponseBody) {
        this.includeResponseBody = includeResponseBody;
    }

    public int getMaxStacktraceLines() {
        return maxStacktraceLines;
    }

    public void setMaxStacktraceLines(int maxStacktraceLines) {
        this.maxStacktraceLines = maxStacktraceLines;
    }

    public int getMaxExtraFields() {
        return maxExtraFields;
    }

    public void setMaxExtraFields(int maxExtraFields) {
        this.maxExtraFields = maxExtraFields;
    }

    public long getSlowMethodThresholdMs() {
        return slowMethodThresholdMs;
    }

    public void setSlowMethodThresholdMs(long slowMethodThresholdMs) {
        this.slowMethodThresholdMs = slowMethodThresholdMs;
    }

    public List<String> getSanitizeFields() {
        return sanitizeFields;
    }

    public void setSanitizeFields(List<String> sanitizeFields) {
        this.sanitizeFields = sanitizeFields;
    }

    public List<String> getExcludeEndpoints() {
        return excludeEndpoints;
    }

    public void setExcludeEndpoints(List<String> excludeEndpoints) {
        this.excludeEndpoints = excludeEndpoints;
    }

    public boolean isIncludeHeaders() {
        return includeHeaders;
    }

    public void setIncludeHeaders(boolean includeHeaders) {
        this.includeHeaders = includeHeaders;
    }

    public boolean isSqlEnabled() {
        return sqlEnabled;
    }

    public void setSqlEnabled(boolean sqlEnabled) {
        this.sqlEnabled = sqlEnabled;
    }

    public long getSqlSlowThresholdMs() {
        return sqlSlowThresholdMs;
    }

    public void setSqlSlowThresholdMs(long sqlSlowThresholdMs) {
        this.sqlSlowThresholdMs = sqlSlowThresholdMs;
    }

    public List<String> getBodyContentTypes() {
        return bodyContentTypes;
    }

    public void setBodyContentTypes(List<String> bodyContentTypes) {
        this.bodyContentTypes = bodyContentTypes;
    }

    public boolean isPartialRedactionEnabled() {
        return partialRedactionEnabled;
    }

    public void setPartialRedactionEnabled(boolean partialRedactionEnabled) {
        this.partialRedactionEnabled = partialRedactionEnabled;
    }
}
