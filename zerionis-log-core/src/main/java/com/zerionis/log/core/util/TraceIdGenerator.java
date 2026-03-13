package com.zerionis.log.core.util;

import java.util.UUID;

/**
 * Generates unique trace identifiers for distributed request correlation.
 *
 * The traceId links all log events from a single request across multiple services.
 * If the incoming HTTP request already carries a trace ID header (X-Trace-Id),
 * the Servlet Filter reuses that value instead of generating a new one.
 *
 * <p>If the application already has Sleuth or Micrometer Tracing configured,
 * auto-configuration skips this generator and uses the existing trace ID.</p>
 *
 * <p>Format: standard UUID v4 (e.g. "550e8400-e29b-41d4-a716-446655440000").</p>
 */
public class TraceIdGenerator {

    /**
     * Generates a new trace ID using UUID v4 (random).
     *
     * @return unique trace ID string
     */
    public String generate() {
        return UUID.randomUUID().toString();
    }
}
