package com.zerionis.log.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents an error captured during request processing.
 *
 * Included inside {@link ZerionisLogEvent} when an exception occurs.
 * The stack trace is truncated by default (25 lines) to keep log size manageable.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ZerionisError {

    /** Fully qualified exception class name (e.g. "java.net.SocketTimeoutException"). */
    private String type;

    /** Exception message (e.g. "Connection timeout after 2000ms"). */
    private String message;

    /** Truncated stack trace as a single string, lines separated by \n. */
    private String stackTrace;

    /** Number of stack trace lines included. */
    private int stackTraceLines;

    /** True if the stack trace was cut because it exceeded the max lines limit. */
    private boolean stackTraceTruncated;

    public ZerionisError() {
    }

    public ZerionisError(String type, String message, String stackTrace,
                         int stackTraceLines, boolean stackTraceTruncated) {
        this.type = type;
        this.message = message;
        this.stackTrace = stackTrace;
        this.stackTraceLines = stackTraceLines;
        this.stackTraceTruncated = stackTraceTruncated;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }

    public int getStackTraceLines() {
        return stackTraceLines;
    }

    public void setStackTraceLines(int stackTraceLines) {
        this.stackTraceLines = stackTraceLines;
    }

    public boolean isStackTraceTruncated() {
        return stackTraceTruncated;
    }

    public void setStackTraceTruncated(boolean stackTraceTruncated) {
        this.stackTraceTruncated = stackTraceTruncated;
    }
}
