package com.zerionis.log.spring.config;

import com.zerionis.log.core.context.ZerionisContext;
import com.zerionis.log.core.format.ZerionisJsonLayout;
import com.zerionis.log.core.format.ZerionisLogFormatter;
import com.zerionis.log.core.util.LogSanitizer;
import com.zerionis.log.core.util.RequestIdGenerator;
import com.zerionis.log.core.util.TraceIdGenerator;
import com.zerionis.log.spring.aspect.ZerionisMethodAspect;
import com.zerionis.log.spring.filter.ZerionisRequestFilter;
import com.zerionis.log.spring.handler.ZerionisExceptionHandler;
import com.zerionis.log.spring.sql.ZerionisDataSourcePostProcessor;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

import jakarta.annotation.PostConstruct;

/**
 * Auto-configuration that activates zerionis-log when the library is on the classpath.
 *
 * <p>Automatically registers:</p>
 * <ul>
 *   <li>{@link ZerionisRequestFilter} — captures HTTP context and emits request events</li>
 *   <li>{@link ZerionisMethodAspect} — detects slow methods and method errors</li>
 *   <li>{@link ZerionisExceptionHandler} — catches unhandled exceptions</li>
 *   <li>Logback JSON layout — transforms all log output to structured JSON</li>
 * </ul>
 *
 * <p>Triggered by Spring Boot auto-configuration. No manual @Import or @ComponentScan needed.
 * Just add the dependency to your pom.xml.</p>
 */
@AutoConfiguration
@ConditionalOnWebApplication
@ConditionalOnClass(jakarta.servlet.Filter.class)
@EnableConfigurationProperties(ZerionisProperties.class)
public class ZerionisAutoConfiguration {

    private final ZerionisProperties properties;

    /** Fallback service name from spring.application.name if zerionis.log.service-name is not set. */
    @Value("${spring.application.name:unknown-service}")
    private String springAppName;

    public ZerionisAutoConfiguration(ZerionisProperties properties) {
        this.properties = properties;
    }

    /**
     * Runs after bean creation. Sets defaults and configures Logback.
     */
    @PostConstruct
    public void init() {
        // Use spring.application.name as fallback for service name
        if (properties.getServiceName() == null || properties.getServiceName().isEmpty()) {
            properties.setServiceName(springAppName);
        }

        // Apply max extra fields limit to ZerionisContext
        ZerionisContext.setMaxFields(properties.getMaxExtraFields());

        // Replace Logback's default console output with JSON layout
        configureLogbackJson();
    }

    /** TraceId generator. Skipped if the application already provides one (e.g. Micrometer). */
    @Bean
    @ConditionalOnMissingBean
    public TraceIdGenerator traceIdGenerator() {
        return new TraceIdGenerator();
    }

    /** RequestId generator. */
    @Bean
    @ConditionalOnMissingBean
    public RequestIdGenerator requestIdGenerator() {
        return new RequestIdGenerator();
    }

    /** JSON log formatter (thread-safe, reusable). */
    @Bean
    @ConditionalOnMissingBean
    public ZerionisLogFormatter zerionisLogFormatter() {
        return new ZerionisLogFormatter();
    }

    /** Sensitive field sanitizer. Redacts passwords, tokens, etc. from log output. */
    @Bean
    @ConditionalOnMissingBean
    public LogSanitizer logSanitizer() {
        return new LogSanitizer(new java.util.HashSet<>(properties.getSanitizeFields()));
    }

    /**
     * Registers the request filter with highest priority so it runs before
     * any other filter and captures the full request lifecycle.
     */
    @Bean
    public FilterRegistrationBean<ZerionisRequestFilter> zerionisRequestFilter(
            TraceIdGenerator traceIdGenerator,
            RequestIdGenerator requestIdGenerator,
            ZerionisLogFormatter formatter,
            LogSanitizer sanitizer) {

        ZerionisRequestFilter filter = new ZerionisRequestFilter(
                traceIdGenerator, requestIdGenerator, formatter, properties, sanitizer);

        FilterRegistrationBean<ZerionisRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /** AOP aspect for slow method detection and method error capture. */
    @Bean
    @ConditionalOnMissingBean
    public ZerionisMethodAspect zerionisMethodAspect(ZerionisLogFormatter formatter) {
        return new ZerionisMethodAspect(formatter, properties);
    }

    /** Global exception handler. */
    @Bean
    @ConditionalOnMissingBean
    public ZerionisExceptionHandler zerionisExceptionHandler() {
        return new ZerionisExceptionHandler();
    }

    /** SQL interceptor. Wraps DataSource beans to monitor query execution. */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "zerionis.log.sql-enabled", havingValue = "true")
    public ZerionisDataSourcePostProcessor zerionisDataSourcePostProcessor(ZerionisLogFormatter formatter) {
        return new ZerionisDataSourcePostProcessor(formatter, properties);
    }

    /**
     * Replaces Logback's default console appender with a JSON layout.
     * After this, all log.info(), log.error(), etc. output structured JSON.
     */
    private void configureLogbackJson() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);

        // Create our JSON layout with service metadata
        ZerionisJsonLayout jsonLayout = new ZerionisJsonLayout();
        jsonLayout.setServiceName(properties.getServiceName());
        jsonLayout.setEnvironment(properties.getEnvironment());
        jsonLayout.setVersion(properties.getVersion());
        jsonLayout.setMaxStackTraceLines(properties.getMaxStacktraceLines());
        jsonLayout.setContext(context);
        jsonLayout.start();

        // Wrap layout in an encoder (Logback requires encoders for appenders)
        LayoutWrappingEncoder<ILoggingEvent> encoder = new LayoutWrappingEncoder<>();
        encoder.setLayout(jsonLayout);
        encoder.setContext(context);
        encoder.start();

        // Create a new console appender with JSON output
        ConsoleAppender<ILoggingEvent> jsonAppender = new ConsoleAppender<>();
        jsonAppender.setName("ZERIONIS_JSON");
        jsonAppender.setEncoder(encoder);
        jsonAppender.setContext(context);
        jsonAppender.start();

        // Remove existing appenders and attach our JSON appender
        rootLogger.detachAndStopAllAppenders();
        rootLogger.addAppender(jsonAppender);
    }
}
