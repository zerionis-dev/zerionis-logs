package com.zerionis.log.core.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Redacts sensitive fields from log data to prevent credential exposure.
 *
 * Automatically detects and replaces values of fields like "password", "token",
 * "authorization", etc. Matching is case-insensitive.
 *
 * <p>Supports:</p>
 * <ul>
 *   <li>Flat map redaction (extra fields)</li>
 *   <li>Deep JSON body redaction (nested objects and arrays)</li>
 *   <li>Header redaction (Authorization, Cookie, etc.)</li>
 *   <li>Partial redaction for tokens/keys (shows first 4 + last 4 chars)</li>
 * </ul>
 *
 * <p>Applied to the {@code extra} field and request/response bodies (when enabled)
 * before they are written to the log output.</p>
 *
 * <p>Additional fields can be added via configuration:
 * {@code zerionis.log.sanitize-fields}.</p>
 */
public class LogSanitizer {

    private static final String REDACTED = "***REDACTED***";

    /** Minimum value length to apply partial redaction. Below this, fully redact. */
    private static final int PARTIAL_REDACTION_MIN_LENGTH = 12;

    /** Number of characters to show at start and end for partial redaction. */
    private static final int PARTIAL_REDACTION_CHARS = 4;

    /** Maximum recursion depth for JSON body redaction to prevent stack overflow. */
    private static final int MAX_REDACTION_DEPTH = 20;

    /** Default sensitive field names (stored lowercase for case-insensitive matching). */
    private static final Set<String> DEFAULT_BLACKLIST = new HashSet<>(Arrays.asList(
            "password",
            "passwd",
            "pass",
            "pwd",
            "token",
            "authorization",
            "secret",
            "apikey",
            "api_key",
            "api-key",
            "accesstoken",
            "access_token",
            "refreshtoken",
            "refresh_token",
            "cardnumber",
            "card_number",
            "cvv",
            "ssn",
            "x-api-key",
            "bearer",
            "jwt",
            "private_key",
            "privatekey",
            "signing_key",
            "session",
            "session_id",
            "sessionid",
            "client_secret",
            "clientsecret",
            "credentials",
            "otp",
            "mfa_code"
    ));

    /** Headers that should always be redacted (stored lowercase). */
    private static final Set<String> SENSITIVE_HEADERS = new HashSet<>(Arrays.asList(
            "authorization",
            "cookie",
            "set-cookie",
            "x-api-key",
            "x-auth-token",
            "proxy-authorization"
    ));

    /** Patterns in header names that indicate sensitive content (stored lowercase). */
    private static final Set<String> SENSITIVE_HEADER_PATTERNS = new HashSet<>(Arrays.asList(
            "token",
            "secret",
            "key",
            "credential",
            "password",
            "auth"
    ));

    /** Combined set of default + user-configured sensitive fields (all lowercase). */
    private final Set<String> blacklist;

    /** Whether partial redaction is enabled (show first/last chars of long values). */
    private boolean partialRedactionEnabled = false;

    /** Lazily initialized ObjectMapper for JSON body parsing. */
    private volatile ObjectMapper objectMapper;

    /** Creates a sanitizer with the default blacklist. */
    public LogSanitizer() {
        this.blacklist = Collections.unmodifiableSet(new HashSet<>(DEFAULT_BLACKLIST));
    }

    /**
     * Creates a sanitizer with additional custom sensitive fields.
     * Custom fields are added to the default blacklist, not replacing it.
     *
     * @param additionalFields extra field names to redact (e.g. "ssn", "dob")
     */
    public LogSanitizer(Set<String> additionalFields) {
        Set<String> combined = new HashSet<>(DEFAULT_BLACKLIST);
        if (additionalFields != null) {
            for (String field : additionalFields) {
                combined.add(field.toLowerCase());
            }
        }
        this.blacklist = Collections.unmodifiableSet(combined);
    }

    /**
     * Creates a sanitizer with additional custom sensitive fields and partial redaction control.
     *
     * @param additionalFields extra field names to redact
     * @param partialRedactionEnabled whether to show partial values for long tokens/keys
     */
    public LogSanitizer(Set<String> additionalFields, boolean partialRedactionEnabled) {
        this(additionalFields);
        this.partialRedactionEnabled = partialRedactionEnabled;
    }

    /**
     * Returns a sanitized copy of the given map. Sensitive field values are
     * redacted. Nested Map and List values are recursively sanitized.
     * The original map is never modified.
     *
     * @param fields map of fields to sanitize
     * @return a new map with sensitive values redacted
     */
    public Map<String, Object> sanitize(Map<String, Object> fields) {
        if (fields == null || fields.isEmpty()) {
            return fields;
        }

        Map<String, Object> sanitized = new HashMap<>(fields.size());

        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            String key = entry.getKey();

            if (isSensitive(key)) {
                sanitized.put(key, redactValue(entry.getValue()));
            } else {
                sanitized.put(key, sanitizeValue(entry.getValue(), 0));
            }
        }

        return sanitized;
    }

    /**
     * Sanitizes a JSON body string by redacting sensitive fields at all nesting levels.
     *
     * <p>Only parses the string if it looks like JSON (starts with '{' or '[').
     * Falls back to returning the original string if parsing fails.</p>
     *
     * @param body the JSON body string
     * @return sanitized JSON string, or original if not valid JSON
     */
    public String sanitizeJsonBody(String body) {
        if (body == null || body.isEmpty()) {
            return body;
        }

        String trimmed = body.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return body;
        }

        try {
            ObjectMapper mapper = getObjectMapper();
            JsonNode tree = mapper.readTree(trimmed);
            redactJsonNode(tree, 0);
            return mapper.writeValueAsString(tree);
        } catch (JsonProcessingException e) {
            // Malformed JSON — return as-is
            return body;
        }
    }

    /**
     * Redacts sensitive header values. Returns the redacted value or the
     * original value if the header is not sensitive.
     *
     * @param headerName the header name (case-insensitive)
     * @param headerValue the header value
     * @return redacted value if the header is sensitive, original otherwise
     */
    public String sanitizeHeader(String headerName, String headerValue) {
        if (headerName == null || headerValue == null) {
            return headerValue;
        }

        if (isHeaderSensitive(headerName)) {
            return redactString(headerValue);
        }

        return headerValue;
    }

    /**
     * Checks whether a header name is considered sensitive and should be redacted.
     *
     * @param headerName the header name to check
     * @return true if the header value should be redacted
     */
    public boolean isHeaderSensitive(String headerName) {
        if (headerName == null) {
            return false;
        }

        String lower = headerName.toLowerCase();

        // Exact match against known sensitive headers
        if (SENSITIVE_HEADERS.contains(lower)) {
            return true;
        }

        // Check if the header name contains any sensitive pattern
        for (String pattern : SENSITIVE_HEADER_PATTERNS) {
            if (lower.contains(pattern)) {
                return true;
            }
        }

        // Also check against the field blacklist (covers custom fields like "x-api-key")
        return blacklist.contains(lower);
    }

    /** Patterns for field-level sensitive detection (more conservative than headers). */
    private static final Set<String> SENSITIVE_FIELD_PATTERNS = new HashSet<>(Arrays.asList(
            "token",
            "secret",
            "password",
            "credential",
            "auth"
    ));

    /**
     * Checks whether a field name is sensitive (exact match or pattern match).
     *
     * @param fieldName field name to check
     * @return true if the field would be redacted
     */
    public boolean isSensitive(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String lower = fieldName.toLowerCase();
        if (blacklist.contains(lower)) {
            return true;
        }
        for (String pattern : SENSITIVE_FIELD_PATTERNS) {
            if (lower.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    // ── Private helpers ──

    /**
     * Recursively sanitizes a value. If it's a Map, sanitize its entries.
     * If it's a List, sanitize each element.
     */
    @SuppressWarnings("unchecked")
    private Object sanitizeValue(Object value, int depth) {
        if (value == null || depth > MAX_REDACTION_DEPTH) {
            return value;
        }

        if (value instanceof Map) {
            Map<String, Object> mapValue = (Map<String, Object>) value;
            Map<String, Object> sanitizedMap = new HashMap<>(mapValue.size());
            for (Map.Entry<String, Object> entry : mapValue.entrySet()) {
                String key = entry.getKey();
                if (isSensitive(key)) {
                    sanitizedMap.put(key, redactValue(entry.getValue()));
                } else {
                    sanitizedMap.put(key, sanitizeValue(entry.getValue(), depth + 1));
                }
            }
            return sanitizedMap;
        }

        if (value instanceof Iterable) {
            java.util.List<Object> sanitizedList = new java.util.ArrayList<>();
            for (Object item : (Iterable<?>) value) {
                sanitizedList.add(sanitizeValue(item, depth + 1));
            }
            return sanitizedList;
        }

        return value;
    }

    /**
     * Redacts a value, applying partial redaction if enabled and the value is a long enough string.
     */
    private Object redactValue(Object value) {
        if (value instanceof String) {
            return redactString((String) value);
        }
        return REDACTED;
    }

    /**
     * Redacts a string value. If partial redaction is enabled and the string
     * is long enough, shows the first 4 and last 4 characters.
     *
     * @param value the string to redact
     * @return redacted string
     */
    private String redactString(String value) {
        if (!partialRedactionEnabled || value == null || value.length() < PARTIAL_REDACTION_MIN_LENGTH) {
            return REDACTED;
        }
        return value.substring(0, PARTIAL_REDACTION_CHARS)
                + "..."
                + value.substring(value.length() - PARTIAL_REDACTION_CHARS);
    }

    /**
     * Recursively traverses a Jackson JSON tree and redacts sensitive fields in-place.
     */
    private void redactJsonNode(JsonNode node, int depth) {
        if (node == null || depth > MAX_REDACTION_DEPTH) {
            return;
        }

        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            Iterator<String> fieldNames = obj.fieldNames();
            java.util.List<String> names = new java.util.ArrayList<>();
            while (fieldNames.hasNext()) {
                names.add(fieldNames.next());
            }

            for (String fieldName : names) {
                if (isSensitive(fieldName)) {
                    JsonNode fieldValue = obj.get(fieldName);
                    if (fieldValue != null && fieldValue.isTextual()) {
                        obj.set(fieldName, new TextNode(redactString(fieldValue.asText())));
                    } else {
                        obj.set(fieldName, new TextNode(REDACTED));
                    }
                } else {
                    redactJsonNode(obj.get(fieldName), depth + 1);
                }
            }
        } else if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++) {
                redactJsonNode(arr.get(i), depth + 1);
            }
        }
    }

    /**
     * Lazy-initializes the ObjectMapper. Only created when JSON body redaction is first needed.
     */
    private ObjectMapper getObjectMapper() {
        if (objectMapper == null) {
            synchronized (this) {
                if (objectMapper == null) {
                    objectMapper = new ObjectMapper();
                }
            }
        }
        return objectMapper;
    }
}
