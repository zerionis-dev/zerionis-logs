package com.zerionis.log.core.format;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerionis.log.core.model.EventType;
import com.zerionis.log.core.model.ZerionisError;
import com.zerionis.log.core.model.ZerionisLogEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ZerionisLogFormatter — JSON serialization of log events.
 */
class ZerionisLogFormatterTest {

    private final ZerionisLogFormatter formatter = new ZerionisLogFormatter();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private ZerionisLogEvent.Builder baseEvent() {
        return ZerionisLogEvent.builder()
                .timestamp(Instant.parse("2026-04-07T12:00:00Z"))
                .level("INFO")
                .service("test-service")
                .environment("test")
                .version("1.0.0")
                .message("test message");
    }

    @Nested
    @DisplayName("format()")
    class FormatTests {

        @Test
        @DisplayName("produces valid JSON")
        void producesValidJson() throws JsonProcessingException {
            ZerionisLogEvent event = baseEvent().build();
            String json = formatter.format(event);
            JsonNode node = objectMapper.readTree(json);
            assertEquals("INFO", node.get("level").asText());
            assertEquals("test message", node.get("message").asText());
            assertEquals("test-service", node.get("service").asText());
        }

        @Test
        @DisplayName("excludes null fields")
        void excludesNullFields() throws JsonProcessingException {
            ZerionisLogEvent event = baseEvent().build();
            String json = formatter.format(event);
            JsonNode node = objectMapper.readTree(json);
            assertFalse(node.has("traceId"));
            assertFalse(node.has("error"));
            assertFalse(node.has("extra"));
        }

        @Test
        @DisplayName("includes extra fields when present")
        void includesExtra() throws JsonProcessingException {
            Map<String, Object> extra = new HashMap<>();
            extra.put("orderId", "ORD-123");
            ZerionisLogEvent event = baseEvent().extra(extra).build();
            String json = formatter.format(event);
            JsonNode node = objectMapper.readTree(json);
            assertEquals("ORD-123", node.get("extra").get("orderId").asText());
        }

        @Test
        @DisplayName("includes error when present")
        void includesError() throws JsonProcessingException {
            ZerionisError error = new ZerionisError(
                    "RuntimeException", "boom", "stack...", 5, false);
            ZerionisLogEvent event = baseEvent()
                    .level("ERROR")
                    .eventType(EventType.APPLICATION_ERROR)
                    .error(error)
                    .build();
            String json = formatter.format(event);
            JsonNode node = objectMapper.readTree(json);
            assertEquals("RuntimeException", node.get("error").get("type").asText());
            assertEquals("boom", node.get("error").get("message").asText());
        }

        @Test
        @DisplayName("serializes timestamp as ISO 8601")
        void timestampIso() throws JsonProcessingException {
            ZerionisLogEvent event = baseEvent().build();
            String json = formatter.format(event);
            assertTrue(json.contains("2026-04-07T12:00:00"));
        }
    }

    @Nested
    @DisplayName("formatSafe()")
    class FormatSafeTests {

        @Test
        @DisplayName("returns valid JSON on success")
        void validJsonOnSuccess() {
            ZerionisLogEvent event = baseEvent().build();
            String json = formatter.formatSafe(event);
            assertDoesNotThrow(() -> objectMapper.readTree(json));
        }

        @Test
        @DisplayName("never throws")
        void neverThrows() {
            String result = formatter.formatSafe(baseEvent().build());
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("formatPretty()")
    class FormatPrettyTests {

        @Test
        @DisplayName("produces indented output")
        void producesIndented() throws JsonProcessingException {
            ZerionisLogEvent event = baseEvent().build();
            String json = formatter.formatPretty(event);
            assertTrue(json.contains("\n"));
            assertTrue(json.contains("  "));
        }
    }
}
