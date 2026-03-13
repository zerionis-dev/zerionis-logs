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
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import javax.annotation.PostConstruct;

/**
 * Auto-configuration for Spring Boot 2.7.x.
 *
 * <p>Functionally identical to the Spring Boot 3 version.
 * Key differences:</p>
 * <ul>
 *   <li>Uses {@code javax.servlet} instead of {@code jakarta.servlet}</li>
 *   <li>Uses {@code @Configuration} instead of {@code @AutoConfiguration}</li>
 *   <li>Registered via {@code META-INF/spring.factories}</li>
 * </ul>
 */
@Configuration
@ConditionalOnWebApplication
@ConditionalOnClass(javax.servlet.Filter.class)
@EnableConfigurationProperties(ZerionisProperties.class)
public class ZerionisAutoConfiguration {

    private final ZerionisProperties properties;

    @Value("${spring.application.name:unknown-service}")
    private String springAppName;

    public ZerionisAutoConfiguration(ZerionisProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        if (properties.getServiceName() == null || properties.getServiceName().isEmpty()) {
            properties.setServiceName(springAppName);
        }
        ZerionisContext.setMaxFields(properties.getMaxExtraFields());
        configureLogbackJson();
    }

    @Bean
    @ConditionalOnMissingBean
    public TraceIdGenerator traceIdGenerator() {
        return new TraceIdGenerator();
    }

    @Bean
    @ConditionalOnMissingBean
    public RequestIdGenerator requestIdGenerator() {
        return new RequestIdGenerator();
    }

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

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.aspectj.lang.ProceedingJoinPoint")
    public ZerionisMethodAspect zerionisMethodAspect(ZerionisLogFormatter formatter) {
        return new ZerionisMethodAspect(formatter, properties);
    }

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

    private void configureLogbackJson() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);

        ZerionisJsonLayout jsonLayout = new ZerionisJsonLayout();
        jsonLayout.setServiceName(properties.getServiceName());
        jsonLayout.setEnvironment(properties.getEnvironment());
        jsonLayout.setVersion(properties.getVersion());
        jsonLayout.setMaxStackTraceLines(properties.getMaxStacktraceLines());
        jsonLayout.setContext(context);
        jsonLayout.start();

        LayoutWrappingEncoder<ILoggingEvent> encoder = new LayoutWrappingEncoder<>();
        encoder.setLayout(jsonLayout);
        encoder.setContext(context);
        encoder.start();

        ConsoleAppender<ILoggingEvent> jsonAppender = new ConsoleAppender<>();
        jsonAppender.setName("ZERIONIS_JSON");
        jsonAppender.setEncoder(encoder);
        jsonAppender.setContext(context);
        jsonAppender.start();

        rootLogger.detachAndStopAllAppenders();
        rootLogger.addAppender(jsonAppender);
    }
}
