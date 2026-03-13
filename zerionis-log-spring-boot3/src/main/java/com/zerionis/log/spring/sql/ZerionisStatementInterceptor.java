package com.zerionis.log.spring.sql;

import com.zerionis.log.core.format.JsonFieldNames;
import com.zerionis.log.core.format.ZerionisLogFormatter;
import com.zerionis.log.core.model.EventType;
import com.zerionis.log.core.model.ZerionisLogEvent;
import com.zerionis.log.core.util.InputValidator;
import com.zerionis.log.core.util.StackTraceUtils;
import com.zerionis.log.spring.config.ZerionisProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Statement;
import java.time.Instant;
import java.util.Set;

/**
 * Dynamic proxy that intercepts JDBC Statement method calls to detect slow queries and errors.
 *
 * <p>Wraps {@link Statement}, {@link java.sql.PreparedStatement}, and
 * {@link java.sql.CallableStatement} to measure SQL execution time.</p>
 *
 * <p>Emits two event types:</p>
 * <ul>
 *   <li>{@code SQL_SLOW} — when a query exceeds the configured threshold</li>
 *   <li>{@code SQL_ERROR} — when a query throws an exception</li>
 * </ul>
 */
class ZerionisStatementInterceptor implements InvocationHandler {

    private static final Logger log = LoggerFactory.getLogger(ZerionisStatementInterceptor.class);

    /** SQL execution methods that should be intercepted. */
    private static final Set<String> EXECUTE_METHODS = Set.of(
            "execute", "executeQuery", "executeUpdate", "executeBatch", "executeLargeUpdate", "executeLargeBatch"
    );

    /** Maximum length for SQL queries in log events. */
    private static final int MAX_SQL_LENGTH = 4096;

    private final Statement target;
    private final String sql;
    private final ZerionisLogFormatter formatter;
    private final ZerionisProperties properties;

    ZerionisStatementInterceptor(Statement target, String sql,
                                  ZerionisLogFormatter formatter,
                                  ZerionisProperties properties) {
        this.target = target;
        this.sql = sql;
        this.formatter = formatter;
        this.properties = properties;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (!EXECUTE_METHODS.contains(method.getName())) {
            return method.invoke(target, args);
        }

        // For plain Statement.execute(sql), the SQL is passed as argument
        String effectiveSql = this.sql;
        if (effectiveSql == null && args != null && args.length > 0 && args[0] instanceof String) {
            effectiveSql = (String) args[0];
        }

        long startTime = System.currentTimeMillis();

        try {
            Object result = method.invoke(target, args);

            long durationMs = System.currentTimeMillis() - startTime;
            if (durationMs >= properties.getSqlSlowThresholdMs()) {
                emitSlowQueryEvent(effectiveSql, durationMs);
            }

            return result;
        } catch (java.lang.reflect.InvocationTargetException e) {
            long durationMs = System.currentTimeMillis() - startTime;
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            emitErrorEvent(effectiveSql, durationMs, cause);
            throw cause;
        }
    }

    private void emitSlowQueryEvent(String sql, long durationMs) {
        String truncatedSql = truncateSql(sql);
        ZerionisLogEvent event = ZerionisLogEvent.builder()
                .timestamp(Instant.now())
                .level("WARN")
                .eventType(EventType.SQL_SLOW)
                .service(properties.getServiceName())
                .environment(properties.getEnvironment())
                .version(properties.getVersion())
                .traceId(MDC.get(JsonFieldNames.MDC_TRACE_ID))
                .requestId(MDC.get(JsonFieldNames.MDC_REQUEST_ID))
                .path(MDC.get(JsonFieldNames.MDC_PATH))
                .httpMethod(MDC.get(JsonFieldNames.MDC_HTTP_METHOD))
                .durationMs(durationMs)
                .message("Slow SQL query: " + durationMs + "ms")
                .addExtra("sql", truncatedSql)
                .build();

        log.warn("{}", event);
    }

    private void emitErrorEvent(String sql, long durationMs, Throwable throwable) {
        String truncatedSql = truncateSql(sql);
        ZerionisLogEvent event = ZerionisLogEvent.builder()
                .timestamp(Instant.now())
                .level("ERROR")
                .eventType(EventType.SQL_ERROR)
                .service(properties.getServiceName())
                .environment(properties.getEnvironment())
                .version(properties.getVersion())
                .traceId(MDC.get(JsonFieldNames.MDC_TRACE_ID))
                .requestId(MDC.get(JsonFieldNames.MDC_REQUEST_ID))
                .path(MDC.get(JsonFieldNames.MDC_PATH))
                .httpMethod(MDC.get(JsonFieldNames.MDC_HTTP_METHOD))
                .durationMs(durationMs)
                .message("SQL error: " + throwable.getMessage())
                .error(StackTraceUtils.fromThrowable(throwable, properties.getMaxStacktraceLines()))
                .addExtra("sql", truncatedSql)
                .build();

        log.error("{}", event);
    }

    private String truncateSql(String sql) {
        if (sql == null) return null;
        if (sql.length() > MAX_SQL_LENGTH) {
            return sql.substring(0, MAX_SQL_LENGTH) + "...[truncated]";
        }
        return sql;
    }

    /**
     * Wraps a Statement in a monitoring proxy.
     */
    static Statement wrap(Statement statement, String sql,
                          ZerionisLogFormatter formatter, ZerionisProperties properties) {
        return (Statement) Proxy.newProxyInstance(
                statement.getClass().getClassLoader(),
                statement.getClass().getInterfaces(),
                new ZerionisStatementInterceptor(statement, sql, formatter, properties)
        );
    }
}
