package com.zerionis.log.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Security-focused tests for LogSanitizer.
 *
 * Verifies that sensitive data is always redacted regardless of casing,
 * field name variations, or attempts to bypass the blacklist.
 */
class LogSanitizerSecurityTest {

    private final LogSanitizer sanitizer = new LogSanitizer();

    // ── Case insensitivity ──

    @Nested
    @DisplayName("Case-insensitive matching")
    class CaseTests {

        @Test
        @DisplayName("redacts regardless of casing")
        void redactsMixedCase() {
            Map<String, Object> fields = new HashMap<>();
            fields.put("PASSWORD", "secret123");
            fields.put("Password", "secret123");
            fields.put("pAsSwOrD", "secret123");
            fields.put("TOKEN", "tok_abc");
            fields.put("Authorization", "Bearer xyz");

            Map<String, Object> result = sanitizer.sanitize(fields);

            for (Object value : result.values()) {
                assertEquals("***REDACTED***", value);
            }
        }
    }

    // ── All default blacklisted fields ──

    @Nested
    @DisplayName("Default blacklist coverage")
    class BlacklistTests {

        @Test
        @DisplayName("redacts all default sensitive fields")
        void redactsAllDefaults() {
            String[] sensitiveFields = {
                    "password", "token", "authorization", "secret",
                    "apikey", "accesstoken", "refreshtoken", "cardnumber", "cvv"
            };

            for (String field : sensitiveFields) {
                Map<String, Object> fields = new HashMap<>();
                fields.put(field, "sensitive_value");

                Map<String, Object> result = sanitizer.sanitize(fields);
                assertEquals("***REDACTED***", result.get(field),
                        "Field '" + field + "' should be redacted");
            }
        }
    }

    // ── Custom fields ──

    @Nested
    @DisplayName("Custom sensitive fields")
    class CustomFieldTests {

        @Test
        @DisplayName("redacts custom fields added via constructor")
        void redactsCustomFields() {
            LogSanitizer custom = new LogSanitizer(
                    new HashSet<>(Arrays.asList("ssn", "dateOfBirth", "creditScore")));

            Map<String, Object> fields = new HashMap<>();
            fields.put("ssn", "123-45-6789");
            fields.put("dateOfBirth", "1990-01-01");
            fields.put("creditScore", 750);
            fields.put("orderId", "ORD-123"); // should NOT be redacted

            Map<String, Object> result = custom.sanitize(fields);

            assertEquals("***REDACTED***", result.get("ssn"));
            assertEquals("***REDACTED***", result.get("dateOfBirth"));
            assertEquals("***REDACTED***", result.get("creditScore"));
            assertEquals("ORD-123", result.get("orderId"));
        }

        @Test
        @DisplayName("custom fields don't remove default blacklist")
        void customPreservesDefaults() {
            LogSanitizer custom = new LogSanitizer(
                    new HashSet<>(Collections.singletonList("ssn")));

            Map<String, Object> fields = new HashMap<>();
            fields.put("password", "secret");
            fields.put("ssn", "123-45-6789");

            Map<String, Object> result = custom.sanitize(fields);

            assertEquals("***REDACTED***", result.get("password"));
            assertEquals("***REDACTED***", result.get("ssn"));
        }
    }

    // ── Immutability ──

    @Nested
    @DisplayName("Original map immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("never modifies the original map")
        void neverModifiesOriginal() {
            Map<String, Object> original = new HashMap<>();
            original.put("password", "my-secret");
            original.put("orderId", "ORD-123");

            Map<String, Object> result = sanitizer.sanitize(original);

            // Original must be untouched
            assertEquals("my-secret", original.get("password"));
            // Result must be redacted
            assertEquals("***REDACTED***", result.get("password"));
            // Non-sensitive values pass through
            assertEquals("ORD-123", result.get("orderId"));
        }
    }

    // ── Edge cases ──

    @Nested
    @DisplayName("Edge cases")
    class EdgeTests {

        @Test
        @DisplayName("handles null map")
        void handlesNullMap() {
            assertNull(sanitizer.sanitize(null));
        }

        @Test
        @DisplayName("handles empty map")
        void handlesEmptyMap() {
            Map<String, Object> empty = new HashMap<>();
            Map<String, Object> result = sanitizer.sanitize(empty);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("handles null field values")
        void handlesNullValues() {
            Map<String, Object> fields = new HashMap<>();
            fields.put("password", null);
            fields.put("normalField", null);

            Map<String, Object> result = sanitizer.sanitize(fields);

            // password key is sensitive, so value gets replaced regardless
            assertEquals("***REDACTED***", result.get("password"));
            assertNull(result.get("normalField"));
        }

        @Test
        @DisplayName("handles map with many fields (DoS resistance)")
        void handlesManyFields() {
            Map<String, Object> fields = new HashMap<>();
            for (int i = 0; i < 10_000; i++) {
                fields.put("field_" + i, "value_" + i);
            }
            fields.put("password", "secret");

            Map<String, Object> result = sanitizer.sanitize(fields);

            assertEquals("***REDACTED***", result.get("password"));
            assertEquals("value_0", result.get("field_0"));
            assertEquals(10_001, result.size());
        }
    }

    // ── isSensitive ──

    @Nested
    @DisplayName("isSensitive method")
    class IsSensitiveTests {

        @Test
        @DisplayName("returns true for sensitive fields")
        void trueForSensitive() {
            assertTrue(sanitizer.isSensitive("password"));
            assertTrue(sanitizer.isSensitive("PASSWORD"));
            assertTrue(sanitizer.isSensitive("Token"));
        }

        @Test
        @DisplayName("returns false for non-sensitive fields")
        void falseForNonSensitive() {
            assertFalse(sanitizer.isSensitive("orderId"));
            assertFalse(sanitizer.isSensitive("username"));
            assertFalse(sanitizer.isSensitive("email"));
        }

        @Test
        @DisplayName("returns false for null")
        void falseForNull() {
            assertFalse(sanitizer.isSensitive(null));
        }
    }
}
