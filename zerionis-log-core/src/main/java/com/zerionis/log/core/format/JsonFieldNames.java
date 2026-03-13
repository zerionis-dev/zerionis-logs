package com.zerionis.log.core.format;

/**
 * Constants for all JSON field names in the log contract.
 *
 * Using constants prevents typos and makes refactoring safe.
 * These names define the standard zerionis-log JSON schema.
 */
public final class JsonFieldNames {

    private JsonFieldNames() {
    }

    // ── Event fields ──

    public static final String TIMESTAMP = "timestamp";
    public static final String LEVEL = "level";
    public static final String EVENT_TYPE = "eventType";

    // ── Service identification ──

    public static final String SERVICE = "service";
    public static final String ENVIRONMENT = "environment";
    public static final String VERSION = "version";

    // ── Correlation ──

    public static final String TRACE_ID = "traceId";
    public static final String REQUEST_ID = "requestId";

    // ── HTTP ──

    public static final String PATH = "path";
    public static final String HTTP_METHOD = "httpMethod";
    public static final String HTTP_STATUS = "httpStatus";
    public static final String CLIENT_IP = "clientIp";

    // ── User ──

    public static final String USER_ID = "userId";

    // ── Code location ──

    public static final String CLASS_NAME = "className";
    public static final String METHOD_NAME = "methodName";
    public static final String DURATION_MS = "durationMs";

    // ── Body capture ──

    public static final String REQUEST_BODY = "requestBody";
    public static final String RESPONSE_BODY = "responseBody";

    // ── Message and error ──

    public static final String MESSAGE = "message";
    public static final String ERROR = "error";

    // ── Extra fields ──

    public static final String EXTRA = "extra";

    // ── Error object fields ──

    public static final String ERROR_TYPE = "type";
    public static final String ERROR_MESSAGE = "message";
    public static final String ERROR_STACK_TRACE = "stackTrace";
    public static final String ERROR_STACK_TRACE_LINES = "stackTraceLines";
    public static final String ERROR_STACK_TRACE_TRUNCATED = "stackTraceTruncated";

    // ── MDC keys (used by the Servlet Filter to populate MDC) ──

    public static final String MDC_TRACE_ID = "zerionis.traceId";
    public static final String MDC_REQUEST_ID = "zerionis.requestId";
    public static final String MDC_PATH = "zerionis.path";
    public static final String MDC_HTTP_METHOD = "zerionis.httpMethod";
    public static final String MDC_CLIENT_IP = "zerionis.clientIp";
    public static final String MDC_USER_ID = "zerionis.userId";
}
