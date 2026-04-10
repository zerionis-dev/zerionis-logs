package com.zerionis.log.core.context;

import com.zerionis.log.core.util.InputValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Public API for adding custom fields to log events.
 *
 * Uses a ThreadLocal internally, so each thread (each HTTP request) has its own
 * isolated map. No conflicts between concurrent requests.
 *
 * <p>Usage:</p>
 * <pre>
 *   ZerionisContext.put("orderId", "ORD-123");
 *   ZerionisContext.put("provider", "conekta");
 * </pre>
 *
 * <p>The data appears in the {@code extra} field of the JSON log output.</p>
 *
 * <p>The Servlet Filter automatically calls {@link #clear()} at the end of each request.
 * Developers do not need to clean up manually.</p>
 *
 * <p>A configurable limit (default: 20) prevents excessive fields per request.</p>
 */
public final class ZerionisContext {

    private static final Logger log = LoggerFactory.getLogger(ZerionisContext.class);

    /** Maximum number of extra fields allowed per request. */
    private static volatile int maxFields = 20;

    /**
     * Per-thread storage for extra fields.
     * Each HTTP request runs on its own thread, so each request gets its own map.
     * Initialized lazily on first {@link #put(String, Object)} call.
     */
    private static final ThreadLocal<Map<String, Object>> CONTEXT =
            ThreadLocal.withInitial(HashMap::new);

    private ZerionisContext() {
    }

    /**
     * Adds an extra field to the current request context.
     *
     * @param key   field name (e.g. "orderId")
     * @param value field value (e.g. "ORD-123")
     */
    public static void put(String key, Object value) {
        // Validate key: reject null, empty, control chars, or overly long keys
        String validatedKey = InputValidator.validateExtraKey(key);
        if (validatedKey == null) {
            return; // Silently ignore invalid keys to avoid breaking application flow
        }

        Map<String, Object> map = CONTEXT.get();

        if (!map.containsKey(validatedKey) && map.size() >= maxFields) {
            log.warn("ZerionisContext: max extra fields limit reached ({}). "
                    + "Field '{}' ignored. Configure zerionis.log.max-extra-fields to increase.",
                    maxFields, validatedKey);
            return;
        }

        // Truncate oversized string values to prevent log bloat
        map.put(validatedKey, InputValidator.truncateExtraValue(value));
    }

    /**
     * Returns the value of an extra field.
     *
     * @param key field name
     * @return the value, or null if not set
     */
    public static Object get(String key) {
        return CONTEXT.get().get(key);
    }

    /**
     * Removes an extra field from the current context.
     *
     * @param key field name to remove
     */
    public static void remove(String key) {
        CONTEXT.get().remove(key);
    }

    /**
     * Returns an unmodifiable copy of all extra fields.
     * Used internally by the JSON layout to include extras in the log output.
     *
     * @return immutable map of extra fields, or empty map if none set
     */
    public static Map<String, Object> getAll() {
        Map<String, Object> map = CONTEXT.get();
        if (map.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new HashMap<>(map));
    }

    /**
     * Restores extra fields from a snapshot (e.g. captured in a parent thread).
     * Used by async decorators to propagate context across thread boundaries.
     * Clears the current context before restoring.
     *
     * @param snapshot the fields to restore (null-safe)
     */
    public static void setAll(Map<String, Object> snapshot) {
        CONTEXT.get().clear();
        if (snapshot != null && !snapshot.isEmpty()) {
            CONTEXT.get().putAll(snapshot);
        }
    }

    /**
     * Clears all extra fields and releases the ThreadLocal reference.
     * Called automatically by the Servlet Filter at the end of each request
     * to prevent memory leaks in thread pools.
     */
    public static void clear() {
        CONTEXT.get().clear();
        CONTEXT.remove();
    }

    /**
     * Sets the maximum number of extra fields allowed per request.
     * Called by auto-configuration when reading {@code zerionis.log.max-extra-fields}.
     *
     * @param max maximum number of fields
     */
    public static void setMaxFields(int max) {
        maxFields = max;
    }

    /**
     * Returns the current max fields limit.
     *
     * @return configured maximum
     */
    public static int getMaxFields() {
        return maxFields;
    }
}
