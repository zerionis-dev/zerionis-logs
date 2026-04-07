package com.zerionis.log.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TraceIdGenerator and RequestIdGenerator.
 */
class GeneratorTest {

    @Nested
    @DisplayName("TraceIdGenerator")
    class TraceIdTests {

        private final TraceIdGenerator gen = new TraceIdGenerator();

        @Test
        @DisplayName("generates valid UUID format")
        void validUuid() {
            String id = gen.generate();
            assertNotNull(id);
            assertTrue(id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        }

        @Test
        @DisplayName("generates unique values")
        void unique() {
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < 1000; i++) {
                assertTrue(ids.add(gen.generate()), "Duplicate at iteration " + i);
            }
        }
    }

    @Nested
    @DisplayName("RequestIdGenerator")
    class RequestIdTests {

        private final RequestIdGenerator gen = new RequestIdGenerator();

        @Test
        @DisplayName("starts with req- prefix")
        void hasPrefix() {
            String id = gen.generate();
            assertTrue(id.startsWith("req-"));
        }

        @Test
        @DisplayName("has correct length (req- + 8 chars)")
        void correctLength() {
            String id = gen.generate();
            assertEquals(12, id.length()); // "req-" (4) + 8
        }

        @Test
        @DisplayName("generates unique values")
        void unique() {
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < 1000; i++) {
                assertTrue(ids.add(gen.generate()), "Duplicate at iteration " + i);
            }
        }
    }
}
