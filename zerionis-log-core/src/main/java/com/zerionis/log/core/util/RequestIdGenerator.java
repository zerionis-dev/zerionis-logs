package com.zerionis.log.core.util;

import java.util.UUID;

/**
 * Generates unique request identifiers for individual HTTP requests.
 *
 * Unlike traceId (which is shared across services for distributed tracing),
 * requestId identifies a single HTTP request within one service.
 *
 * <p>Format: "req-" prefix + first 8 characters of a UUID
 * (e.g. "req-550e8400").</p>
 */
public class RequestIdGenerator {

    private static final String PREFIX = "req-";
    private static final int UUID_SHORT_LENGTH = 8;

    /**
     * Generates a new request ID with "req-" prefix and short UUID.
     *
     * @return unique request ID (e.g. "req-550e8400")
     */
    public String generate() {
        String shortUuid = UUID.randomUUID().toString().substring(0, UUID_SHORT_LENGTH);
        return PREFIX + shortUuid;
    }
}
