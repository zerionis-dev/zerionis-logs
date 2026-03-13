package com.zerionis.log.core.format;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.LayoutBase;
import com.zerionis.log.core.context.ZerionisContext;
import com.zerionis.log.core.model.EventType;
import com.zerionis.log.core.model.ZerionisLogEvent;
import com.zerionis.log.core.util.InputValidator;
import com.zerionis.log.core.util.LogSanitizer;
import com.zerionis.log.core.util.StackTraceUtils;

import java.time.Instant;
import java.util.Map;

/**
 * Custom Logback layout that converts all log events to structured JSON.
 *
 * <p>Intercepts every {@code log.info()}, {@code log.error()}, etc. call and transforms
 * it into a structured JSON object.</p>
 *
 * <p>How it works:</p>
 * <ol>
 *   <li>Logback captures the developer's log call</li>
 *   <li>Instead of formatting as plain text, this layout takes over</li>
 *   <li>Reads MDC values (populated by the Servlet Filter) for request context</li>
 *   <li>Reads ZerionisContext for developer-defined extra fields</li>
 *   <li>Builds a {@link ZerionisLogEvent} with all available data</li>
 *   <li>Serializes to JSON and returns it as the log output</li>
 * </ol>
 *
 * <p>Result: all application logs come out as structured JSON, even plain
 * {@code log.info("some message")} calls.</p>
 */
public class ZerionisJsonLayout extends LayoutBase<ILoggingEvent> {

    private final ZerionisLogFormatter formatter = new ZerionisLogFormatter();
    private final LogSanitizer sanitizer = new LogSanitizer();

    // ── Configurable properties (set via auto-configuration) ──

    private String serviceName;
    private String environment;
    private String version;
    private int maxStackTraceLines = 25;

    /**
     * Called by Logback for every log event.
     * Transforms the event into a JSON string with a trailing newline.
     *
     * @param loggingEvent the Logback log event
     * @return JSON string followed by \n (one log per line)
     */
    @Override
    public String doLayout(ILoggingEvent loggingEvent) {

        // Check if an internal component (filter, aspect, SQL interceptor) passed
        // a pre-built event as argument: log.info("{}", zerionisLogEvent)
        // If so, serialize it directly — no need to rebuild from MDC.
        Object[] args = loggingEvent.getArgumentArray();
        if (args != null && args.length == 1 && args[0] instanceof ZerionisLogEvent) {
            return formatter.formatSafe((ZerionisLogEvent) args[0]) + "\n";
        }

        // For regular application logs (log.info, log.error, etc.),
        // build a structured event from MDC context and the log message.
        Map<String, String> mdc = loggingEvent.getMDCPropertyMap();

        Map<String, Object> extra = ZerionisContext.getAll();
        if (!extra.isEmpty()) {
            extra = sanitizer.sanitize(extra);
        }

        IThrowableProxy throwableProxy = loggingEvent.getThrowableProxy();
        Throwable throwable = null;
        if (throwableProxy instanceof ThrowableProxy) {
            throwable = ((ThrowableProxy) throwableProxy).getThrowable();
        }

        ZerionisLogEvent event = ZerionisLogEvent.builder()
                .timestamp(Instant.ofEpochMilli(loggingEvent.getTimeStamp()))
                .level(loggingEvent.getLevel().toString())
                .eventType(throwable != null ? EventType.APPLICATION_ERROR : null)
                .service(serviceName)
                .environment(environment)
                .version(version)
                .traceId(mdc.get(JsonFieldNames.MDC_TRACE_ID))
                .requestId(mdc.get(JsonFieldNames.MDC_REQUEST_ID))
                .path(mdc.get(JsonFieldNames.MDC_PATH))
                .httpMethod(mdc.get(JsonFieldNames.MDC_HTTP_METHOD))
                .clientIp(mdc.get(JsonFieldNames.MDC_CLIENT_IP))
                .userId(mdc.get(JsonFieldNames.MDC_USER_ID))
                .className(loggingEvent.getLoggerName())
                .message(InputValidator.truncateMessage(loggingEvent.getFormattedMessage()))
                .error(throwable != null
                        ? StackTraceUtils.fromThrowable(throwable, maxStackTraceLines)
                        : null)
                .extra(extra.isEmpty() ? null : extra)
                .build();

        return formatter.formatSafe(event) + "\n";
    }

    // ── Setters for auto-configuration ──

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setMaxStackTraceLines(int maxStackTraceLines) {
        this.maxStackTraceLines = maxStackTraceLines;
    }
}
