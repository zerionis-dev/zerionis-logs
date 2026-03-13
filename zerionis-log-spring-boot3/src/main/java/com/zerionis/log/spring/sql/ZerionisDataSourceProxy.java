package com.zerionis.log.spring.sql;

import com.zerionis.log.core.format.ZerionisLogFormatter;
import com.zerionis.log.spring.config.ZerionisProperties;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.logging.Logger;

/**
 * DataSource wrapper that intercepts JDBC operations for SQL monitoring.
 *
 * <p>Wraps the original DataSource and returns proxied Connections that
 * produce proxied Statements. Each Statement execution is monitored for
 * slow queries and errors.</p>
 *
 * <p>Activated when {@code zerionis.log.sql-enabled=true}.</p>
 */
public class ZerionisDataSourceProxy implements DataSource {

    private final DataSource delegate;
    private final ZerionisLogFormatter formatter;
    private final ZerionisProperties properties;

    public ZerionisDataSourceProxy(DataSource delegate, ZerionisLogFormatter formatter,
                                    ZerionisProperties properties) {
        this.delegate = delegate;
        this.formatter = formatter;
        this.properties = properties;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return wrapConnection(delegate.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return wrapConnection(delegate.getConnection(username, password));
    }

    private Connection wrapConnection(Connection connection) {
        return (Connection) Proxy.newProxyInstance(
                connection.getClass().getClassLoader(),
                new Class[]{Connection.class},
                new ConnectionHandler(connection)
        );
    }

    /**
     * InvocationHandler for Connection that wraps created Statements.
     */
    private class ConnectionHandler implements InvocationHandler {
        private final Connection target;

        ConnectionHandler(Connection target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            try {
                Object result = method.invoke(target, args);

                if (result instanceof PreparedStatement && args != null && args.length > 0 && args[0] instanceof String) {
                    return ZerionisStatementInterceptor.wrap(
                            (Statement) result, (String) args[0], formatter, properties);
                }
                if (result instanceof Statement) {
                    return ZerionisStatementInterceptor.wrap(
                            (Statement) result, null, formatter, properties);
                }

                return result;
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause() != null ? e.getCause() : e;
            }
        }
    }

    // ── Delegate methods ──

    @Override
    public PrintWriter getLogWriter() throws SQLException { return delegate.getLogWriter(); }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException { delegate.setLogWriter(out); }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException { delegate.setLoginTimeout(seconds); }

    @Override
    public int getLoginTimeout() throws SQLException { return delegate.getLoginTimeout(); }

    @Override
    public Logger getParentLogger() { try { return delegate.getParentLogger(); } catch (Exception e) { return Logger.getLogger("zerionis.sql"); } }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException { return delegate.unwrap(iface); }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException { return delegate.isWrapperFor(iface); }

    /** Returns the original DataSource being proxied. */
    public DataSource getDelegate() { return delegate; }
}
