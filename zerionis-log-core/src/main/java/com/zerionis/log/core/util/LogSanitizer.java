package com.zerionis.log.core.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Redacts sensitive fields from log data to prevent credential exposure.
 *
 * Automatically detects and replaces values of fields like "password", "token",
 * "authorization", etc. with "***REDACTED***". Matching is case-insensitive.
 *
 * <p>Applied to the {@code extra} field and request/response bodies (when enabled)
 * before they are written to the log output.</p>
 *
 * <p>Additional fields can be added via configuration:
 * {@code zerionis.log.sanitize-fields}.</p>
 */
public class LogSanitizer {

    private static final String REDACTED = "***REDACTED***";

    /** Default sensitive field names (stored lowercase for case-insensitive matching). */
    private static final Set<String> DEFAULT_BLACKLIST = new HashSet<>(Arrays.asList(
            "password",
            "token",
            "authorization",
            "secret",
            "apikey",
            "accesstoken",
            "refreshtoken",
            "cardnumber",
            "cvv"
    ));

    /** Combined set of default + user-configured sensitive fields (all lowercase). */
    private final Set<String> blacklist;

    /** Creates a sanitizer with the default blacklist. */
    public LogSanitizer() {
        this.blacklist = new HashSet<>(DEFAULT_BLACKLIST);
    }

    /**
     * Creates a sanitizer with additional custom sensitive fields.
     * Custom fields are added to the default blacklist, not replacing it.
     *
     * @param additionalFields extra field names to redact (e.g. "ssn", "dob")
     */
    public LogSanitizer(Set<String> additionalFields) {
        this.blacklist = new HashSet<>(DEFAULT_BLACKLIST);
        if (additionalFields != null) {
            for (String field : additionalFields) {
                this.blacklist.add(field.toLowerCase());
            }
        }
    }

    /**
     * Returns a sanitized copy of the given map. Sensitive field values are
     * replaced with "***REDACTED***". The original map is never modified.
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

            if (blacklist.contains(key.toLowerCase())) {
                sanitized.put(key, REDACTED);
            } else {
                sanitized.put(key, entry.getValue());
            }
        }

        return sanitized;
    }

    /**
     * Checks whether a field name is in the sensitive blacklist.
     *
     * @param fieldName field name to check
     * @return true if the field would be redacted
     */
    public boolean isSensitive(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        return blacklist.contains(fieldName.toLowerCase());
    }
}
