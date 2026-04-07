package com.zerionis.log.core.context;

import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ZerionisContext — ThreadLocal-based request context.
 */
class ZerionisContextTest {

    @AfterEach
    void cleanup() {
        ZerionisContext.clear();
        ZerionisContext.setMaxFields(20);
    }

    @Nested
    @DisplayName("Basic CRUD")
    class BasicTests {

        @Test
        @DisplayName("put and get a field")
        void putAndGet() {
            ZerionisContext.put("orderId", "ORD-123");
            assertEquals("ORD-123", ZerionisContext.get("orderId"));
        }

        @Test
        @DisplayName("get returns null for missing key")
        void getMissing() {
            assertNull(ZerionisContext.get("nonexistent"));
        }

        @Test
        @DisplayName("remove deletes a field")
        void remove() {
            ZerionisContext.put("orderId", "ORD-123");
            ZerionisContext.remove("orderId");
            assertNull(ZerionisContext.get("orderId"));
        }

        @Test
        @DisplayName("clear removes all fields")
        void clear() {
            ZerionisContext.put("a", "1");
            ZerionisContext.put("b", "2");
            ZerionisContext.clear();
            assertTrue(ZerionisContext.getAll().isEmpty());
        }

        @Test
        @DisplayName("overwrite existing key")
        void overwrite() {
            ZerionisContext.put("key", "v1");
            ZerionisContext.put("key", "v2");
            assertEquals("v2", ZerionisContext.get("key"));
        }
    }

    @Nested
    @DisplayName("getAll")
    class GetAllTests {

        @Test
        @DisplayName("returns all fields")
        void returnsAll() {
            ZerionisContext.put("a", "1");
            ZerionisContext.put("b", "2");
            Map<String, Object> all = ZerionisContext.getAll();
            assertEquals("1", all.get("a"));
            assertEquals("2", all.get("b"));
            assertEquals(2, all.size());
        }

        @Test
        @DisplayName("returns empty map when nothing set")
        void emptyWhenNothingSet() {
            assertTrue(ZerionisContext.getAll().isEmpty());
        }

        @Test
        @DisplayName("returned map is unmodifiable")
        void unmodifiable() {
            ZerionisContext.put("key", "val");
            Map<String, Object> all = ZerionisContext.getAll();
            assertThrows(UnsupportedOperationException.class, () -> all.put("new", "val"));
        }
    }

    @Nested
    @DisplayName("Max fields limit")
    class LimitTests {

        @Test
        @DisplayName("enforces max fields limit")
        void enforceLimit() {
            ZerionisContext.setMaxFields(3);
            ZerionisContext.put("a", "1");
            ZerionisContext.put("b", "2");
            ZerionisContext.put("c", "3");
            ZerionisContext.put("d", "4"); // should be ignored

            Map<String, Object> all = ZerionisContext.getAll();
            assertEquals(3, all.size());
            assertNull(all.get("d"));
        }

        @Test
        @DisplayName("allows overwriting existing key even at limit")
        void allowOverwriteAtLimit() {
            ZerionisContext.setMaxFields(2);
            ZerionisContext.put("a", "1");
            ZerionisContext.put("b", "2");
            ZerionisContext.put("a", "updated"); // overwrite, not new
            assertEquals("updated", ZerionisContext.get("a"));
        }
    }

    @Nested
    @DisplayName("Thread isolation")
    class ThreadTests {

        @Test
        @DisplayName("different threads have isolated contexts")
        void threadIsolation() throws Exception {
            ZerionisContext.put("main", "mainVal");

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Object> threadValue = new AtomicReference<>();
            AtomicReference<Object> threadMainValue = new AtomicReference<>();

            Thread t = new Thread(() -> {
                ZerionisContext.put("thread", "threadVal");
                threadValue.set(ZerionisContext.get("thread"));
                threadMainValue.set(ZerionisContext.get("main"));
                ZerionisContext.clear();
                latch.countDown();
            });
            t.start();
            latch.await();

            assertEquals("threadVal", threadValue.get());
            assertNull(threadMainValue.get()); // main's value not visible
            assertEquals("mainVal", ZerionisContext.get("main")); // still there
            assertNull(ZerionisContext.get("thread")); // thread's value not visible
        }
    }

    @Nested
    @DisplayName("Input validation integration")
    class ValidationTests {

        @Test
        @DisplayName("rejects null key silently")
        void rejectsNullKey() {
            ZerionisContext.put(null, "value");
            assertTrue(ZerionisContext.getAll().isEmpty());
        }

        @Test
        @DisplayName("rejects empty key silently")
        void rejectsEmptyKey() {
            ZerionisContext.put("", "value");
            assertTrue(ZerionisContext.getAll().isEmpty());
        }

        @Test
        @DisplayName("truncates long string values")
        void truncatesLongValue() {
            String longVal = "x".repeat(100_000);
            ZerionisContext.put("key", longVal);
            Object stored = ZerionisContext.get("key");
            assertTrue(stored instanceof String);
            assertTrue(((String) stored).length() <= 4096 + 15);
        }
    }
}
