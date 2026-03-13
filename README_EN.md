# zerionis-log

**Automatic structured logging for Spring Boot.**

Add a single Maven dependency and all your logs become structured JSON with traceId, HTTP context, timing, and sensitive data sanitization. Zero code changes required.

---

## The problem

```java
// What developers typically write
log.error("Error in process");
log.info("Entered method");
catch (Exception e) { e.printStackTrace(); }
```

Unstructured logs with no context, no correlation, no standard format. When something breaks in production, finding the root cause takes longer than it should.

## The solution

```xml
<dependency>
    <groupId>com.zerionis</groupId>
    <artifactId>zerionis-log-spring-boot3</artifactId>
    <version>1.0.0</version>
</dependency>
```

Zero mandatory configuration. Activates automatically via Spring Boot auto-configuration.

**Before:**
```
ERROR c.e.PaymentService - Error in process
```

**After:**
```json
{
  "timestamp": "2026-03-10T14:32:01.123Z",
  "level": "ERROR",
  "eventType": "REQUEST_ERROR",
  "service": "payments-api",
  "environment": "production",
  "traceId": "550e8400-e29b-41d4-a716-446655440000",
  "requestId": "req-a1b2c3",
  "path": "/payments/charge",
  "httpMethod": "POST",
  "httpStatus": 500,
  "className": "com.example.PaymentService",
  "methodName": "processCharge",
  "durationMs": 2340,
  "message": "Error processing payment charge",
  "error": {
    "type": "RedisConnectionException",
    "message": "Connection timeout after 2000ms"
  }
}
```

---

## Installation

### Spring Boot 3.x (Java 17+)

```xml
<dependency>
    <groupId>com.zerionis</groupId>
    <artifactId>zerionis-log-spring-boot3</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Spring Boot 2.7.x (Java 11+)

```xml
<dependency>
    <groupId>com.zerionis</groupId>
    <artifactId>zerionis-log-spring-boot2</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## What it does automatically

| Feature | How |
|---------|-----|
| Structured JSON | Custom Logback Layout replaces default format |
| traceId per request | Auto-generated UUID v4, compatible with Sleuth/Micrometer |
| HTTP context | Servlet Filter captures path, method, IP, requestId |
| Request timing | Duration measured in the Filter |
| Exception capture | ControllerAdvice + AOP |
| Slow method detection | AOP Aspect with configurable threshold |
| Sensitive data sanitization | Auto-detection of password, token, secret fields |

---

## Configuration

Everything has sensible defaults. Only configure what you need:

```yaml
zerionis:
  log:
    service-name: payments-api          # Service name (or uses spring.application.name)
    environment: production             # Environment
    version: 2.1.8                      # Service version
    request-start-enabled: false        # Log request start (default: false)
    max-stacktrace-lines: 25           # Stack trace lines (default: 25)
    max-extra-fields: 20               # Max extra fields (default: 20)
    slow-method-threshold-ms: 1000     # Slow method threshold in ms (default: 1000)
    sanitize-enabled: true             # Sanitize sensitive fields (default: true)
    exclude-endpoints:                 # Endpoints to exclude from logging
      - /actuator/health
      - /favicon.ico
```

---

## Extra fields

Add custom context to your logs:

```java
ZerionisContext.put("orderId", "ORD-123");
ZerionisContext.put("provider", "conekta");
ZerionisContext.put("customerId", "C001");
```

Automatically included in the `extra` JSON field:

```json
{
  "extra": {
    "orderId": "ORD-123",
    "provider": "conekta",
    "customerId": "C001"
  }
}
```

Context is automatically cleared at the end of each request.

---

## Event taxonomy

| eventType | Description |
|-----------|-------------|
| `REQUEST_END` | HTTP request completed |
| `REQUEST_ERROR` | HTTP request with error |
| `METHOD_SLOW` | Method exceeded duration threshold |
| `METHOD_ERROR` | Exception in intercepted method |
| `APPLICATION_ERROR` | Unhandled error |
| `REQUEST_START` | Request start (disabled by default) |

---

## Project architecture

```
zerionis-log/
├── zerionis-log-core/              → Pure logic, no Spring dependency
│   ├── model/                      → ZerionisLogEvent, ZerionisError, EventType
│   ├── context/                    → ZerionisContext (ThreadLocal API)
│   ├── util/                       → TraceId, RequestId, StackTrace, Sanitizer
│   └── format/                     → JSON Layout, Formatter, Field names
│
├── zerionis-log-spring-boot2/      → Starter for Spring Boot 2.7.x (javax.servlet)
└── zerionis-log-spring-boot3/      → Starter for Spring Boot 3.x (jakarta.servlet)
```

---

## Compatibility

| Artifact | Spring Boot | Java |
|----------|------------|------|
| `zerionis-log-spring-boot2` | 2.7.x | 11+ |
| `zerionis-log-spring-boot3` | 3.x | 17+ |

---

## License

Apache 2.0
