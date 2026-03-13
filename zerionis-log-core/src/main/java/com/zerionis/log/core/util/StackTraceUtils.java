package com.zerionis.log.core.util;

import com.zerionis.log.core.model.ZerionisError;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Converts exceptions into {@link ZerionisError} objects with truncated stack traces.
 *
 * Full stack traces in Java can exceed 100 lines. In high-traffic production systems,
 * this generates oversized logs that increase storage costs without adding useful information.
 * Most of the relevant data is in the first 20-30 lines.
 *
 * <p>The max number of lines is configurable via {@code zerionis.log.max-stacktrace-lines}
 * (default: 25).</p>
 */
public class StackTraceUtils {

    /** Default maximum number of stack trace lines to include. */
    public static final int DEFAULT_MAX_LINES = 25;

    /**
     * Converts an exception into a {@link ZerionisError} with a truncated stack trace.
     *
     * @param throwable the captured exception
     * @param maxLines  maximum number of stack trace lines to include
     * @return a ZerionisError ready to be included in the log event
     */
    public static ZerionisError fromThrowable(Throwable throwable, int maxLines) {
        if (throwable == null) {
            return null;
        }

        String type = throwable.getClass().getName();
        String message = throwable.getMessage();
        String fullStackTrace = getFullStackTrace(throwable);

        String[] lines = fullStackTrace.split("\n");
        boolean truncated = lines.length > maxLines;
        int linesToInclude = Math.min(lines.length, maxLines);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < linesToInclude; i++) {
            if (i > 0) {
                sb.append("\n");
            }
            sb.append(lines[i]);
        }

        if (truncated) {
            sb.append("\n... (truncated)");
        }

        return new ZerionisError(type, message, sb.toString(), linesToInclude, truncated);
    }

    /**
     * Converts an exception using the default max lines (25).
     *
     * @param throwable the captured exception
     * @return a ZerionisError with truncated stack trace
     */
    public static ZerionisError fromThrowable(Throwable throwable) {
        return fromThrowable(throwable, DEFAULT_MAX_LINES);
    }

    /**
     * Converts a Throwable to its full string representation, including caused-by chain.
     */
    private static String getFullStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }
}
