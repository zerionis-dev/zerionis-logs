package com.zerionis.log.core.util;

import com.zerionis.log.core.model.ZerionisError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for StackTraceUtils — exception to ZerionisError conversion.
 */
class StackTraceUtilsTest {

    @Nested
    @DisplayName("Basic conversion")
    class BasicTests {

        @Test
        @DisplayName("converts exception to ZerionisError")
        void convertsException() {
            RuntimeException ex = new RuntimeException("test error");
            ZerionisError error = StackTraceUtils.fromThrowable(ex);

            assertEquals("java.lang.RuntimeException", error.getType());
            assertEquals("test error", error.getMessage());
            assertNotNull(error.getStackTrace());
            assertTrue(error.getStackTrace().contains("RuntimeException"));
        }

        @Test
        @DisplayName("handles null throwable")
        void handlesNull() {
            assertNull(StackTraceUtils.fromThrowable(null));
        }

        @Test
        @DisplayName("handles exception with null message")
        void handlesNullMessage() {
            RuntimeException ex = new RuntimeException((String) null);
            ZerionisError error = StackTraceUtils.fromThrowable(ex);
            assertNull(error.getMessage());
            assertNotNull(error.getType());
        }
    }

    @Nested
    @DisplayName("Truncation")
    class TruncationTests {

        @Test
        @DisplayName("truncates to specified max lines")
        void truncatesToMaxLines() {
            RuntimeException ex = createDeepException(100);
            ZerionisError error = StackTraceUtils.fromThrowable(ex, 5);

            String[] lines = error.getStackTrace().split("\n");
            // 5 lines + 1 "... (truncated)" line
            assertTrue(lines.length <= 6);
            assertTrue(error.getStackTrace().contains("(truncated)"));
            assertTrue(error.isStackTraceTruncated());
        }

        @Test
        @DisplayName("does not truncate when within limit")
        void noTruncateWithinLimit() {
            RuntimeException ex = new RuntimeException("short");
            ZerionisError error = StackTraceUtils.fromThrowable(ex, 1000);

            assertFalse(error.isStackTraceTruncated());
            assertFalse(error.getStackTrace().contains("(truncated)"));
        }

        @Test
        @DisplayName("default is 25 lines")
        void defaultMaxLines() {
            assertEquals(25, StackTraceUtils.DEFAULT_MAX_LINES);
        }
    }

    @Nested
    @DisplayName("Caused-by chain")
    class CausedByTests {

        @Test
        @DisplayName("includes caused-by in stack trace")
        void includesCausedBy() {
            Exception cause = new IllegalArgumentException("root cause");
            RuntimeException ex = new RuntimeException("wrapper", cause);
            ZerionisError error = StackTraceUtils.fromThrowable(ex, 200);

            assertTrue(error.getStackTrace().contains("IllegalArgumentException"));
        }
    }

    private RuntimeException createDeepException(int depth) {
        try {
            throwRecursive(depth);
        } catch (RuntimeException e) {
            return e;
        }
        return new RuntimeException("should not reach");
    }

    private void throwRecursive(int depth) {
        if (depth <= 0) {
            throw new RuntimeException("deep error");
        }
        throwRecursive(depth - 1);
    }
}
