package com.zerionis.log.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for deep JSON body redaction, header sanitization, partial redaction,
 * and pattern-based field matching.
 */
class LogSanitizerDeepTest {

    private final LogSanitizer sanitizer = new LogSanitizer();

    // ── Deep JSON body redaction ──

    @Nested
    @DisplayName("JSON body redaction")
    class JsonBodyTests {

        @Test
        @DisplayName("redacts sensitive fields in flat JSON")
        void redactsFlatJson() {
            String json = "{\"username\":\"john\",\"password\":\"secret123\"}";
            String result = sanitizer.sanitizeJsonBody(json);
            assertTrue(result.contains("***REDACTED***"));
            assertTrue(result.contains("john"));
            assertFalse(result.contains("secret123"));
        }

        @Test
        @DisplayName("redacts nested sensitive fields")
        void redactsNestedJson() {
            String json = "{\"user\":{\"name\":\"john\",\"credentials\":{\"password\":\"secret\",\"token\":\"abc\"}}}";
            String result = sanitizer.sanitizeJsonBody(json);
            assertFalse(result.contains("secret"));
            assertFalse(result.contains("abc"));
            assertTrue(result.contains("john"));
        }

        @Test
        @DisplayName("redacts sensitive fields inside arrays")
        void redactsArrayJson() {
            String json = "[{\"password\":\"s1\"},{\"password\":\"s2\"}]";
            String result = sanitizer.sanitizeJsonBody(json);
            assertFalse(result.contains("s1"));
            assertFalse(result.contains("s2"));
        }

        @Test
        @DisplayName("returns non-JSON strings unchanged")
        void nonJsonPassesThrough() {
            String text = "not a json body";
            assertEquals(text, sanitizer.sanitizeJsonBody(text));
        }

        @Test
        @DisplayName("returns malformed JSON unchanged")
        void malformedJsonPassesThrough() {
            String bad = "{\"password\": broken}";
            assertEquals(bad, sanitizer.sanitizeJsonBody(bad));
        }

        @Test
        @DisplayName("handles null and empty")
        void handlesNullEmpty() {
            assertNull(sanitizer.sanitizeJsonBody(null));
            assertEquals("", sanitizer.sanitizeJsonBody(""));
        }

        @Test
        @DisplayName("handles deeply nested JSON without stack overflow")
        void handlesDeepNesting() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 50; i++) {
                sb.append("{\"level").append(i).append("\":");
            }
            sb.append("\"leaf\"");
            for (int i = 0; i < 50; i++) {
                sb.append("}");
            }
            // Should not throw
            String result = sanitizer.sanitizeJsonBody(sb.toString());
            assertNotNull(result);
        }
    }

    // ── Deep Map redaction ──

    @Nested
    @DisplayName("Deep Map redaction")
    class DeepMapTests {

        @Test
        @DisplayName("redacts nested maps")
        void redactsNestedMaps() {
            Map<String, Object> inner = new HashMap<>();
            inner.put("password", "secret");
            inner.put("name", "john");

            Map<String, Object> outer = new HashMap<>();
            outer.put("user", inner);
            outer.put("orderId", "ORD-1");

            Map<String, Object> result = sanitizer.sanitize(outer);
            @SuppressWarnings("unchecked")
            Map<String, Object> resultInner = (Map<String, Object>) result.get("user");
            assertEquals("***REDACTED***", resultInner.get("password"));
            assertEquals("john", resultInner.get("name"));
            assertEquals("ORD-1", result.get("orderId"));
        }

        @Test
        @DisplayName("redacts inside lists")
        void redactsInsideLists() {
            Map<String, Object> item = new HashMap<>();
            item.put("token", "abc123");

            Map<String, Object> fields = new HashMap<>();
            fields.put("items", Arrays.asList(item));

            Map<String, Object> result = sanitizer.sanitize(fields);
            @SuppressWarnings("unchecked")
            List<Object> items = (List<Object>) result.get("items");
            @SuppressWarnings("unchecked")
            Map<String, Object> resultItem = (Map<String, Object>) items.get(0);
            assertEquals("***REDACTED***", resultItem.get("token"));
        }

        @Test
        @DisplayName("handles deeply nested maps without stack overflow")
        void handlesDeepMaps() {
            Map<String, Object> current = new HashMap<>();
            current.put("password", "secret");
            for (int i = 0; i < 50; i++) {
                Map<String, Object> wrapper = new HashMap<>();
                wrapper.put("level" + i, current);
                current = wrapper;
            }
            // Should not throw
            Map<String, Object> result = sanitizer.sanitize(current);
            assertNotNull(result);
        }
    }

    // ── Header sanitization ──

    @Nested
    @DisplayName("Header sanitization")
    class HeaderTests {

        @Test
        @DisplayName("redacts known sensitive headers")
        void redactsKnownHeaders() {
            assertEquals("***REDACTED***", sanitizer.sanitizeHeader("Authorization", "Bearer xyz"));
            assertEquals("***REDACTED***", sanitizer.sanitizeHeader("Cookie", "session=abc"));
            assertEquals("***REDACTED***", sanitizer.sanitizeHeader("Set-Cookie", "id=xyz"));
            assertEquals("***REDACTED***", sanitizer.sanitizeHeader("X-Api-Key", "key123"));
        }

        @Test
        @DisplayName("redacts headers matching sensitive patterns")
        void redactsPatternHeaders() {
            assertEquals("***REDACTED***", sanitizer.sanitizeHeader("X-Auth-Token", "tok"));
            assertEquals("***REDACTED***", sanitizer.sanitizeHeader("X-Secret-Key", "sec"));
            assertEquals("***REDACTED***", sanitizer.sanitizeHeader("X-Password-Hash", "hash"));
        }

        @Test
        @DisplayName("passes through non-sensitive headers")
        void passesNonSensitive() {
            assertEquals("application/json", sanitizer.sanitizeHeader("Content-Type", "application/json"));
            assertEquals("gzip", sanitizer.sanitizeHeader("Accept-Encoding", "gzip"));
        }

        @Test
        @DisplayName("is case-insensitive")
        void caseInsensitive() {
            assertTrue(sanitizer.isHeaderSensitive("AUTHORIZATION"));
            assertTrue(sanitizer.isHeaderSensitive("authorization"));
            assertTrue(sanitizer.isHeaderSensitive("Authorization"));
        }

        @Test
        @DisplayName("handles null")
        void handlesNull() {
            // null header name → value passes through unchanged
            assertEquals("val", sanitizer.sanitizeHeader(null, "val"));
            assertNull(sanitizer.sanitizeHeader("Content-Type", null));
            assertFalse(sanitizer.isHeaderSensitive(null));
        }
    }

    // ── Pattern matching (isSensitive) ──

    @Nested
    @DisplayName("Pattern-based field matching")
    class PatternTests {

        @Test
        @DisplayName("matches fields containing 'token'")
        void matchesToken() {
            assertTrue(sanitizer.isSensitive("userToken"));
            assertTrue(sanitizer.isSensitive("access_token_v2"));
            assertTrue(sanitizer.isSensitive("tokenExpiry"));
        }

        @Test
        @DisplayName("matches fields containing 'secret'")
        void matchesSecret() {
            assertTrue(sanitizer.isSensitive("clientSecret"));
            assertTrue(sanitizer.isSensitive("my_secret_key"));
        }

        @Test
        @DisplayName("matches fields containing 'password'")
        void matchesPassword() {
            assertTrue(sanitizer.isSensitive("userPassword"));
            assertTrue(sanitizer.isSensitive("password_hash"));
            assertTrue(sanitizer.isSensitive("old_password"));
        }

        @Test
        @DisplayName("matches fields containing 'auth'")
        void matchesAuth() {
            assertTrue(sanitizer.isSensitive("authCode"));
            assertTrue(sanitizer.isSensitive("oauth_token"));
        }

        @Test
        @DisplayName("matches fields containing 'credential'")
        void matchesCredential() {
            assertTrue(sanitizer.isSensitive("userCredentials"));
            assertTrue(sanitizer.isSensitive("credential_id"));
        }

        @Test
        @DisplayName("does NOT match 'key' alone (too broad)")
        void doesNotMatchKeyAlone() {
            assertFalse(sanitizer.isSensitive("key"));
            assertFalse(sanitizer.isSensitive("primaryKey"));
            assertFalse(sanitizer.isSensitive("keyboard"));
        }

        @Test
        @DisplayName("does not false-positive on normal fields")
        void noFalsePositives() {
            assertFalse(sanitizer.isSensitive("orderId"));
            assertFalse(sanitizer.isSensitive("username"));
            assertFalse(sanitizer.isSensitive("email"));
            assertFalse(sanitizer.isSensitive("status"));
            assertFalse(sanitizer.isSensitive("amount"));
        }
    }

    // ── Partial redaction ──

    @Nested
    @DisplayName("Partial redaction")
    class PartialRedactionTests {

        @Test
        @DisplayName("shows first4...last4 when enabled and value long enough")
        void showsPartial() {
            LogSanitizer partial = new LogSanitizer(null, true);
            Map<String, Object> fields = new HashMap<>();
            fields.put("token", "abcdefghijklmnop"); // 16 chars > 12 min

            Map<String, Object> result = partial.sanitize(fields);
            String redacted = (String) result.get("token");
            assertEquals("abcd...mnop", redacted);
        }

        @Test
        @DisplayName("fully redacts short values even with partial enabled")
        void fullyRedactsShort() {
            LogSanitizer partial = new LogSanitizer(null, true);
            Map<String, Object> fields = new HashMap<>();
            fields.put("token", "short"); // 5 chars < 12 min

            Map<String, Object> result = partial.sanitize(fields);
            assertEquals("***REDACTED***", result.get("token"));
        }

        @Test
        @DisplayName("fully redacts when partial is disabled (default)")
        void fullyRedactsWhenDisabled() {
            Map<String, Object> fields = new HashMap<>();
            fields.put("token", "abcdefghijklmnop");

            Map<String, Object> result = sanitizer.sanitize(fields);
            assertEquals("***REDACTED***", result.get("token"));
        }
    }

    // ── Expanded blacklist ──

    @Nested
    @DisplayName("Expanded default blacklist")
    class ExpandedBlacklistTests {

        @Test
        @DisplayName("redacts new default fields")
        void redactsNewDefaults() {
            String[] newFields = {
                    "passwd", "pass", "pwd", "bearer", "jwt",
                    "private_key", "privatekey", "signing_key",
                    "session", "session_id", "sessionid",
                    "client_secret", "clientsecret", "credentials",
                    "otp", "mfa_code"
            };

            for (String field : newFields) {
                Map<String, Object> fields = new HashMap<>();
                fields.put(field, "sensitive");

                Map<String, Object> result = sanitizer.sanitize(fields);
                assertEquals("***REDACTED***", result.get(field),
                        "Field '" + field + "' should be redacted");
            }
        }
    }
}
