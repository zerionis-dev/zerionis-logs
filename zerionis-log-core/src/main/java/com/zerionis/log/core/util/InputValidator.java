package com.zerionis.log.core.util;

import java.util.regex.Pattern;

/**
 * Validates and sanitizes external input before it enters the logging pipeline.
 *
 * <p>Protects against log injection, XSS via log dashboards, oversized payloads,
 * and malformed trace/request IDs from untrusted HTTP headers.</p>
 *
 * <p>All methods are static and null-safe. Invalid input is either cleaned or
 * replaced with a safe default — never passed through raw.</p>
 */
public final class InputValidator {

    private InputValidator() {
    }

    // ── Trace ID / Request ID validation ──

    /** Allowed characters for trace IDs: alphanumeric, hyphens, underscores, dots. */
    private static final Pattern SAFE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9._\\-]{1,128}$");

    /** Maximum length for trace IDs and request IDs. */
    private static final int MAX_ID_LENGTH = 128;

    /** Maximum length for HTTP request paths stored in logs. */
    private static final int MAX_PATH_LENGTH = 2048;

    /** Maximum length for log message fields. */
    private static final int MAX_MESSAGE_LENGTH = 10_000;

    /** Maximum length for HTTP request/response body in logs. */
    private static final int MAX_BODY_LENGTH = 8192;

    /** Maximum length for individual extra field values (as string representation). */
    private static final int MAX_EXTRA_VALUE_LENGTH = 4096;

    /** Maximum length for extra field keys. */
    private static final int MAX_EXTRA_KEY_LENGTH = 128;

    /** Maximum length for client IP strings. */
    private static final int MAX_IP_LENGTH = 45; // IPv6 max = 45 chars

    /** Pattern to match valid IPv4 addresses. */
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$");

    /** Pattern to match valid IPv6 addresses (simplified — allows hex groups and colons). */
    private static final Pattern IPV6_PATTERN = Pattern.compile(
            "^([0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}$");

    /**
     * Validates a trace ID received from an external HTTP header (X-Trace-Id).
     *
     * <p>Only allows alphanumeric characters, hyphens, underscores, and dots.
     * Maximum length: 128 characters. If invalid, returns null so the caller
     * generates a new safe ID instead.</p>
     *
     * @param traceId the raw trace ID from the HTTP header
     * @return the trace ID if valid, or null if it should be regenerated
     */
    public static String validateTraceId(String traceId) {
        if (traceId == null || traceId.isEmpty()) {
            return null;
        }
        if (traceId.length() > MAX_ID_LENGTH) {
            return null;
        }
        if (!SAFE_ID_PATTERN.matcher(traceId).matches()) {
            return null;
        }
        return traceId;
    }

    /**
     * Truncates a request path to the maximum allowed length.
     *
     * <p>Extremely long paths (e.g. from fuzzing or path traversal attempts)
     * could inflate log payloads. This limits them to 2048 characters.</p>
     *
     * @param path the raw request URI
     * @return truncated path, or null if input is null
     */
    public static String truncatePath(String path) {
        if (path == null) {
            return null;
        }
        if (path.length() > MAX_PATH_LENGTH) {
            return path.substring(0, MAX_PATH_LENGTH) + "...[truncated]";
        }
        return path;
    }

    /**
     * Truncates a log message to prevent oversized log entries.
     *
     * @param message the log message
     * @return truncated message, or null if input is null
     */
    public static String truncateMessage(String message) {
        if (message == null) {
            return null;
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            return message.substring(0, MAX_MESSAGE_LENGTH) + "...[truncated]";
        }
        return message;
    }

    /**
     * Validates and truncates an extra field key.
     *
     * <p>Keys must be non-null, non-empty, and no longer than 128 characters.
     * Only printable ASCII characters are allowed (no control characters).</p>
     *
     * @param key the extra field key
     * @return sanitized key, or null if invalid
     */
    public static String validateExtraKey(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        if (key.length() > MAX_EXTRA_KEY_LENGTH) {
            key = key.substring(0, MAX_EXTRA_KEY_LENGTH);
        }
        // Reject keys with control characters (prevents log injection via field names)
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                return null;
            }
        }
        return key;
    }

    /**
     * Truncates an extra field value to prevent oversized log entries.
     *
     * <p>If the value is a String longer than 4096 characters, it is truncated.
     * Non-string values are returned as-is (Jackson handles serialization).</p>
     *
     * @param value the extra field value
     * @return truncated value if String, original value otherwise
     */
    public static Object truncateExtraValue(Object value) {
        if (value instanceof String) {
            String str = (String) value;
            if (str.length() > MAX_EXTRA_VALUE_LENGTH) {
                return str.substring(0, MAX_EXTRA_VALUE_LENGTH) + "...[truncated]";
            }
        }
        return value;
    }

    /**
     * Validates a client IP address from X-Forwarded-For or remoteAddr.
     *
     * <p>Accepts valid IPv4 and IPv6 addresses. Rejects anything else to prevent
     * injection of arbitrary strings via the X-Forwarded-For header.</p>
     *
     * @param ip the raw IP string
     * @return the IP if valid, or "unknown" if malformed
     */
    public static String validateClientIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return "unknown";
        }
        // Trim and limit length first
        ip = ip.trim();
        if (ip.length() > MAX_IP_LENGTH) {
            return "unknown";
        }
        // Validate format
        if (IPV4_PATTERN.matcher(ip).matches() || IPV6_PATTERN.matcher(ip).matches()) {
            return ip;
        }
        // Allow localhost variants
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip) || "127.0.0.1".equals(ip)) {
            return ip;
        }
        return "unknown";
    }

    /**
     * Truncates an HTTP request or response body to prevent oversized log entries.
     *
     * @param body the raw body content
     * @return truncated body, or null if input is null
     */
    public static String truncateBody(String body) {
        if (body == null) {
            return null;
        }
        if (body.length() > MAX_BODY_LENGTH) {
            return body.substring(0, MAX_BODY_LENGTH) + "...[truncated]";
        }
        return body;
    }

    /**
     * Strips HTML/script tags from a string value.
     *
     * <p>Prevents stored XSS if log values are rendered in a web dashboard
     * without proper output encoding. This is a defense-in-depth
     * measure — dashboards should also escape output, but we sanitize at the source.</p>
     *
     * @param value the raw string value
     * @return value with HTML tags removed
     */
    public static String stripHtml(String value) {
        if (value == null) {
            return null;
        }
        // Remove anything that looks like an HTML/XML tag
        return value.replaceAll("<[^>]*>", "");
    }

    /**
     * Removes control characters (CR, LF, TAB, etc.) from a string.
     *
     * <p>Prevents log injection attacks where an attacker injects newline characters
     * to forge fake log entries. Tabs are preserved as they're harmless.</p>
     *
     * @param value the raw string value
     * @return value with control characters removed (except tab)
     */
    public static String stripControlChars(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            // Allow printable chars + tab (0x09), remove other control chars
            if (c >= 0x20 || c == 0x09) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
