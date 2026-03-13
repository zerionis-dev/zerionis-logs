package com.zerionis.log.spring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for zerionis-log.
 * Bound to the {@code zerionis.log} prefix in application.yml.
 *
 * <p>All properties have sensible defaults. Zero configuration is required.</p>
 *
 * <p>Identical to the Spring Boot 3 version. Duplicated because the auto-configuration
 * classes live in separate modules.</p>
 */
@ConfigurationProperties(prefix = "zerionis.log")
public class ZerionisProperties {

    private String serviceName;
    private String environment;
    private String version;
    private boolean requestStartEnabled = false;
    private boolean includeRequestBody = false;
    private boolean includeResponseBody = false;
    private int maxStacktraceLines = 25;
    private int maxExtraFields = 20;
    private long slowMethodThresholdMs = 1000;
    private boolean sanitizeEnabled = true;
    private List<String> sanitizeFields = new ArrayList<>();
    private List<String> excludeEndpoints = new ArrayList<>();
    private boolean sqlEnabled = false;
    private long sqlSlowThresholdMs = 500;

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public boolean isRequestStartEnabled() { return requestStartEnabled; }
    public void setRequestStartEnabled(boolean requestStartEnabled) { this.requestStartEnabled = requestStartEnabled; }
    public boolean isIncludeRequestBody() { return includeRequestBody; }
    public void setIncludeRequestBody(boolean includeRequestBody) { this.includeRequestBody = includeRequestBody; }
    public boolean isIncludeResponseBody() { return includeResponseBody; }
    public void setIncludeResponseBody(boolean includeResponseBody) { this.includeResponseBody = includeResponseBody; }
    public int getMaxStacktraceLines() { return maxStacktraceLines; }
    public void setMaxStacktraceLines(int maxStacktraceLines) { this.maxStacktraceLines = maxStacktraceLines; }
    public int getMaxExtraFields() { return maxExtraFields; }
    public void setMaxExtraFields(int maxExtraFields) { this.maxExtraFields = maxExtraFields; }
    public long getSlowMethodThresholdMs() { return slowMethodThresholdMs; }
    public void setSlowMethodThresholdMs(long slowMethodThresholdMs) { this.slowMethodThresholdMs = slowMethodThresholdMs; }
    public boolean isSanitizeEnabled() { return sanitizeEnabled; }
    public void setSanitizeEnabled(boolean sanitizeEnabled) { this.sanitizeEnabled = sanitizeEnabled; }
    public List<String> getSanitizeFields() { return sanitizeFields; }
    public void setSanitizeFields(List<String> sanitizeFields) { this.sanitizeFields = sanitizeFields; }
    public List<String> getExcludeEndpoints() { return excludeEndpoints; }
    public void setExcludeEndpoints(List<String> excludeEndpoints) { this.excludeEndpoints = excludeEndpoints; }
    public boolean isSqlEnabled() { return sqlEnabled; }
    public void setSqlEnabled(boolean sqlEnabled) { this.sqlEnabled = sqlEnabled; }
    public long getSqlSlowThresholdMs() { return sqlSlowThresholdMs; }
    public void setSqlSlowThresholdMs(long sqlSlowThresholdMs) { this.sqlSlowThresholdMs = sqlSlowThresholdMs; }
}
