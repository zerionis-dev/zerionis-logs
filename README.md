# zerionis-log

[![Maven Central](https://img.shields.io/maven-central/v/com.zerionis/zerionis-log-spring-boot3)](https://central.sonatype.com/artifact/com.zerionis/zerionis-log-spring-boot3)
[![CI](https://github.com/zerionis-dev/zerionis-logs/actions/workflows/ci.yml/badge.svg)](https://github.com/zerionis-dev/zerionis-logs/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

**Structured JSON logging for Spring Boot. Zero code changes.**

Add one Maven dependency and every log becomes structured JSON with traceId, HTTP context, timing, and sensitive data redaction. No annotations, no manual wiring — it just works.

---

## The problem

```java
// What most projects look like
log.error("Error en proceso");
log.info("Entró al método");
catch (Exception e) { e.printStackTrace(); }
```

Unstructured logs. No correlation. No context. When something breaks in production, you spend more time searching logs than fixing the bug.

## The fix

```xml
<dependency>
    <groupId>com.zerionis</groupId>
    <artifactId>zerionis-log-spring-boot3</artifactId>
    <version>1.0.1</version>
</dependency>
```

That's it. No config required. Your logs go from this:

```
ERROR c.e.PaymentService - Error en proceso
```

To this:

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

Every request. Every error. Automatically.

---

## Quick start

### 1. Add the dependency

**Spring Boot 3.x** (Java 17+):

```xml
<dependency>
    <groupId>com.zerionis</groupId>
    <artifactId>zerionis-log-spring-boot3</artifactId>
    <version>1.0.1</version>
</dependency>
```

**Spring Boot 2.7.x** (Java 11+):

```xml
<dependency>
    <groupId>com.zerionis</groupId>
    <artifactId>zerionis-log-spring-boot2</artifactId>
    <version>1.0.1</version>
</dependency>
```

### 2. Run your app

```bash
mvn spring-boot:run
```

Every HTTP request now produces structured JSON logs. No code changes needed.

### 3. (Optional) Add custom context

```java
@RestController
public class OrderController {

    @PostMapping("/orders")
    public Order create(@RequestBody OrderRequest req) {
        // Add business context to your logs — shows up in the "extra" field
        ZerionisContext.put("orderId", req.getId());
        ZerionisContext.put("customerId", req.getCustomerId());

        return orderService.create(req);
        // Context is automatically cleared after the request
    }
}
```

The extra fields appear in every log event for that request:

```json
{
  "extra": {
    "orderId": "ORD-123",
    "customerId": "C001"
  }
}
```

---

## What it does automatically

| Feature | How |
|---------|-----|
| **Structured JSON** | Custom Logback layout replaces the default format |
| **traceId per request** | UUID v4, compatible with Sleuth/Micrometer |
| **HTTP context** | Servlet Filter captures path, method, IP, requestId |
| **Request timing** | Duration measured in the filter |
| **Exception capture** | ControllerAdvice + AOP |
| **Slow method detection** | AOP Aspect with configurable threshold |
| **Sensitive data redaction** | Auto-detects password, token, secret, apiKey fields |

---

## Configuration

Everything has sensible defaults. Only configure what you need:

```yaml
zerionis:
  log:
    service-name: payments-api          # or uses spring.application.name
    environment: production             # environment tag
    version: 2.1.8                      # service version tag
    slow-method-threshold-ms: 1000      # slow method threshold in ms (default: 1000)
    max-stacktrace-lines: 25            # stack trace lines (default: 25)
    max-extra-fields: 20                # max extra fields per request (default: 20)
    sanitize-enabled: true              # redact sensitive fields (default: true)
    sanitize-fields:                    # add your own sensitive fields
      - ssn
      - creditScore
    exclude-endpoints:                  # endpoints excluded from logging
      - /actuator/health
      - /favicon.ico
```

---

## Event types

| eventType | Description |
|-----------|-------------|
| `REQUEST_END` | HTTP request completed |
| `REQUEST_ERROR` | HTTP request with error |
| `METHOD_SLOW` | Method exceeded duration threshold |
| `METHOD_ERROR` | Exception in intercepted method |
| `APPLICATION_ERROR` | Unhandled error |
| `REQUEST_START` | Request start (disabled by default) |

---

## Security

zerionis-log includes built-in protection against common logging attack vectors:

- **Log injection** — control characters stripped, JSON serialization escapes newlines
- **Trace ID injection** — strict validation (`[a-zA-Z0-9._-]`, max 128 chars)
- **Client IP spoofing** — IPv4/IPv6 format validation on `X-Forwarded-For`
- **Payload inflation** — truncation limits on paths, messages, stack traces, and extra fields
- **Sensitive data exposure** — automatic redaction of password, token, secret, apiKey, cardNumber, cvv
- **Supply chain** — strict checksums, dependency convergence enforcement, SBOM generation

See [SECURITY.md](docs/SECURITY.md) for the full security specification.

---

## Project structure

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

## Contributing

Found a bug? Have an idea? [Open an issue](https://github.com/zerionis-dev/zerionis-logs/issues).

Pull requests are welcome. Please open an issue first to discuss what you'd like to change.

---

## License

[Apache 2.0](LICENSE)
