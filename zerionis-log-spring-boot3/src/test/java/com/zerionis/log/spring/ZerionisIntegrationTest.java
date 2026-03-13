package com.zerionis.log.spring;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerionis.log.core.format.ZerionisLogFormatter;
import com.zerionis.log.core.util.LogSanitizer;
import com.zerionis.log.core.util.RequestIdGenerator;
import com.zerionis.log.core.util.TraceIdGenerator;
import com.zerionis.log.spring.aspect.ZerionisMethodAspect;
import com.zerionis.log.spring.config.ZerionisAutoConfiguration;
import com.zerionis.log.spring.config.ZerionisProperties;
import com.zerionis.log.spring.filter.ZerionisRequestFilter;
import com.zerionis.log.spring.handler.ZerionisExceptionHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for zerionis-log Spring Boot 3 auto-configuration.
 * Verifies that filter, aspect, exception handler, and JSON layout work end-to-end.
 */
@SpringBootTest(classes = {TestApplication.class, TestController.class, IgnoredController.class, TestSecurityConfig.class})
@AutoConfigureMockMvc
class ZerionisIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ListAppender<ILoggingEvent> logCapture;

    @BeforeEach
    void setUp() {
        // Capture log output from the filter and aspect loggers
        logCapture = new ListAppender<>();
        logCapture.start();

        Logger filterLogger = (Logger) LoggerFactory.getLogger(ZerionisRequestFilter.class);
        filterLogger.addAppender(logCapture);

        Logger aspectLogger = (Logger) LoggerFactory.getLogger(ZerionisMethodAspect.class);
        aspectLogger.addAppender(logCapture);
    }

    // ── Auto-Configuration Tests ──

    @Nested
    @DisplayName("Auto-configuration loads all beans")
    class AutoConfigTests {

        @Test
        @DisplayName("ZerionisProperties is bound")
        void propertiesBound() {
            ZerionisProperties props = context.getBean(ZerionisProperties.class);
            assertEquals("test-service", props.getServiceName());
            assertEquals("test", props.getEnvironment());
            assertEquals("1.0.1-test", props.getVersion());
            assertEquals(50, props.getSlowMethodThresholdMs());
            assertTrue(props.isIncludeRequestBody());
            assertTrue(props.isIncludeResponseBody());
        }

        @Test
        @DisplayName("All beans are registered")
        void beansRegistered() {
            assertNotNull(context.getBean(TraceIdGenerator.class));
            assertNotNull(context.getBean(RequestIdGenerator.class));
            assertNotNull(context.getBean(ZerionisLogFormatter.class));
            assertNotNull(context.getBean(LogSanitizer.class));
            assertNotNull(context.getBean(ZerionisMethodAspect.class));
            assertNotNull(context.getBean(ZerionisExceptionHandler.class));
        }

        @Test
        @DisplayName("Filter is registered with highest precedence")
        void filterRegistered() {
            @SuppressWarnings("unchecked")
            FilterRegistrationBean<ZerionisRequestFilter> registration =
                    context.getBean("zerionisRequestFilter", FilterRegistrationBean.class);
            assertNotNull(registration);
            assertEquals(Integer.MIN_VALUE, registration.getOrder());
        }
    }

    // ── Filter Tests ──

    @Nested
    @DisplayName("Request filter")
    class FilterTests {

        @Test
        @DisplayName("Emits REQUEST_END for successful GET")
        void requestEnd() throws Exception {
            mockMvc.perform(get("/api/hello"))
                    .andExpect(status().isOk());

            String logJson = findLogWithEventType("REQUEST_END");
            assertNotNull(logJson, "Should emit REQUEST_END event");

            JsonNode node = objectMapper.readTree(logJson);
            assertEquals("INFO", node.get("level").asText());
            assertEquals("test-service", node.get("service").asText());
            assertEquals("test", node.get("environment").asText());
            assertEquals("/api/hello", node.get("path").asText());
            assertEquals("GET", node.get("httpMethod").asText());
            assertEquals(200, node.get("httpStatus").asInt());
            assertNotNull(node.get("traceId"));
            assertNotNull(node.get("requestId"));
            assertTrue(node.get("durationMs").asLong() >= 0);
        }

        @Test
        @DisplayName("Emits REQUEST_ERROR for 500 errors")
        void requestError() throws Exception {
            try {
                mockMvc.perform(get("/api/error"));
            } catch (Exception ignored) {
                // Exception propagates through MockMvc — expected
            }

            String logJson = findLogWithEventType("REQUEST_ERROR");
            assertNotNull(logJson, "Should emit REQUEST_ERROR event");

            JsonNode node = objectMapper.readTree(logJson);
            assertEquals("ERROR", node.get("level").asText());
            assertEquals("REQUEST_ERROR", node.get("eventType").asText());
        }

        @Test
        @DisplayName("Reuses X-Trace-Id header when valid")
        void reusesTraceId() throws Exception {
            String customTraceId = "abc-123-def-456";
            mockMvc.perform(get("/api/hello").header("X-Trace-Id", customTraceId))
                    .andExpect(status().isOk());

            String logJson = findLogWithEventType("REQUEST_END");
            JsonNode node = objectMapper.readTree(logJson);
            assertEquals(customTraceId, node.get("traceId").asText());
        }

        @Test
        @DisplayName("Rejects malicious X-Trace-Id and generates new")
        void rejectsMaliciousTraceId() throws Exception {
            mockMvc.perform(get("/api/hello").header("X-Trace-Id", "<script>alert(1)</script>"))
                    .andExpect(status().isOk());

            String logJson = findLogWithEventType("REQUEST_END");
            JsonNode node = objectMapper.readTree(logJson);
            // Should NOT contain the malicious trace ID
            assertNotEquals("<script>alert(1)</script>", node.get("traceId").asText());
            // Should be a valid UUID
            assertTrue(node.get("traceId").asText().matches("[a-zA-Z0-9._\\-]+"));
        }

        @Test
        @DisplayName("Excludes configured endpoints")
        void excludesEndpoints() throws Exception {
            logCapture.list.clear();
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk());

            String logJson = findLogWithPath("/actuator/health");
            assertNull(logJson, "Should not log excluded endpoint");
        }

        @Test
        @DisplayName("Includes extra fields from ZerionisContext")
        void includesExtraFields() throws Exception {
            mockMvc.perform(get("/api/extra"))
                    .andExpect(status().isOk());

            String logJson = findLogWithEventType("REQUEST_END");
            JsonNode node = objectMapper.readTree(logJson);
            JsonNode extra = node.get("extra");
            assertNotNull(extra, "Should include extra fields");
            assertEquals("ORD-123", extra.get("orderId").asText());
            assertEquals("conekta", extra.get("provider").asText());
        }

        @Test
        @DisplayName("Captures client IP from X-Forwarded-For")
        void capturesClientIp() throws Exception {
            mockMvc.perform(get("/api/hello").header("X-Forwarded-For", "192.168.1.100"))
                    .andExpect(status().isOk());

            String logJson = findLogWithEventType("REQUEST_END");
            JsonNode node = objectMapper.readTree(logJson);
            assertEquals("192.168.1.100", node.get("clientIp").asText());
        }
    }

    // ── Aspect Tests ──

    @Nested
    @DisplayName("Method aspect")
    class AspectTests {

        @Test
        @DisplayName("Emits METHOD_SLOW for slow methods")
        void methodSlow() throws Exception {
            mockMvc.perform(get("/api/slow"))
                    .andExpect(status().isOk());

            String logJson = findLogWithEventType("METHOD_SLOW");
            assertNotNull(logJson, "Should emit METHOD_SLOW event");

            JsonNode node = objectMapper.readTree(logJson);
            assertEquals("WARN", node.get("level").asText());
            assertTrue(node.get("durationMs").asLong() >= 50);
            assertTrue(node.get("message").asText().contains("Slow method"));
            assertNotNull(node.get("className"));
            assertNotNull(node.get("methodName"));
        }

        @Test
        @DisplayName("Emits METHOD_ERROR for exceptions")
        void methodError() throws Exception {
            try {
                mockMvc.perform(get("/api/error"));
            } catch (Exception ignored) {
                // Exception propagates through MockMvc — expected
            }

            String logJson = findLogWithEventType("METHOD_ERROR");
            assertNotNull(logJson, "Should emit METHOD_ERROR event");

            JsonNode node = objectMapper.readTree(logJson);
            assertEquals("ERROR", node.get("level").asText());
            assertNotNull(node.get("error"));
            assertEquals("java.lang.RuntimeException", node.get("error").get("type").asText());
        }

        @Test
        @DisplayName("@LogIgnore skips method tracking")
        void logIgnoreSkipsMethod() throws Exception {
            logCapture.list.clear();
            mockMvc.perform(get("/api/ignored"))
                    .andExpect(status().isOk());

            // Should NOT emit METHOD_SLOW even though it takes > 50ms
            String logJson = findLogWithEventType("METHOD_SLOW");
            assertNull(logJson, "@LogIgnore should prevent METHOD_SLOW");
        }

        @Test
        @DisplayName("@LogSkip skips method tracking")
        void logSkipSkipsMethod() throws Exception {
            logCapture.list.clear();
            mockMvc.perform(get("/api/skipped"))
                    .andExpect(status().isOk());

            String logJson = findLogWithEventType("METHOD_SLOW");
            assertNull(logJson, "@LogSkip should prevent METHOD_SLOW");
        }

        @Test
        @DisplayName("@LogIgnore on class skips all methods")
        void logIgnoreOnClassSkipsAll() throws Exception {
            logCapture.list.clear();
            mockMvc.perform(get("/api/class-ignored"))
                    .andExpect(status().isOk());

            String logJson = findLogWithEventType("METHOD_SLOW");
            assertNull(logJson, "@LogIgnore on class should prevent METHOD_SLOW");
        }
    }

    // ── Body Capture Tests ──

    @Nested
    @DisplayName("Body capture")
    class BodyCaptureTests {

        @Test
        @DisplayName("Captures request body on POST")
        void capturesRequestBody() throws Exception {
            String requestJson = "{\"key\":\"value\"}";
            mockMvc.perform(post("/api/echo")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk());

            String logJson = findLogWithEventType("REQUEST_END");
            JsonNode node = objectMapper.readTree(logJson);
            assertNotNull(node.get("requestBody"), "Should capture request body");
            assertEquals(requestJson, node.get("requestBody").asText());
        }

        @Test
        @DisplayName("Captures response body")
        void capturesResponseBody() throws Exception {
            mockMvc.perform(get("/api/hello"))
                    .andExpect(status().isOk());

            String logJson = findLogWithEventType("REQUEST_END");
            JsonNode node = objectMapper.readTree(logJson);
            assertNotNull(node.get("responseBody"), "Should capture response body");
            assertTrue(node.get("responseBody").asText().contains("hello"));
        }

        @Test
        @DisplayName("Response still reaches the client when body capture is enabled")
        void responseReachesClient() throws Exception {
            mockMvc.perform(get("/api/hello"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("hello"));
        }
    }

    // ── Helper methods ──

    /**
     * Finds the first log event containing the given event type from captured logs.
     * Extracts the ZerionisLogEvent from the log call arguments and formats it as JSON.
     */
    private String findLogWithEventType(String eventType) {
        ZerionisLogFormatter fmt = new ZerionisLogFormatter();
        for (ILoggingEvent event : logCapture.list) {
            Object[] args = event.getArgumentArray();
            if (args != null && args.length > 0 && args[0] instanceof com.zerionis.log.core.model.ZerionisLogEvent) {
                com.zerionis.log.core.model.ZerionisLogEvent zerEvent =
                        (com.zerionis.log.core.model.ZerionisLogEvent) args[0];
                if (zerEvent.getEventType() != null && zerEvent.getEventType().name().equals(eventType)) {
                    return fmt.formatSafe(zerEvent);
                }
            }
        }
        return null;
    }

    /**
     * Finds the first log event containing the given path.
     */
    private String findLogWithPath(String path) {
        ZerionisLogFormatter fmt = new ZerionisLogFormatter();
        for (ILoggingEvent event : logCapture.list) {
            Object[] args = event.getArgumentArray();
            if (args != null && args.length > 0 && args[0] instanceof com.zerionis.log.core.model.ZerionisLogEvent) {
                com.zerionis.log.core.model.ZerionisLogEvent zerEvent =
                        (com.zerionis.log.core.model.ZerionisLogEvent) args[0];
                String json = fmt.formatSafe(zerEvent);
                if (json.contains("\"path\":\"" + path + "\"")) {
                    return json;
                }
            }
        }
        return null;
    }
}
