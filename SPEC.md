# zerionis-log — Multi-language Spec

All language implementations MUST produce structured JSON log events following this contract.

## Log Contract (v1)

Every log event is a single JSON line. Fields marked *required* MUST always be present.

### Common fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `timestamp` | ISO 8601 | yes | UTC, e.g. `2026-03-13T10:30:00.123Z` |
| `level` | string | yes | `DEBUG`, `INFO`, `WARNING`, `ERROR` |
| `message` | string | yes | Log message (sanitized, max 10,000 chars) |
| `service` | string | yes | Application/service name |
| `environment` | string | no | `production`, `staging`, `development` |
| `version` | string | no | Application version |
| `traceId` | string | no | Distributed trace ID (UUID v4 or OTel 32-hex) |
| `requestId` | string | no | Per-request correlation ID |
| `eventType` | string | no | See Event Types below |

### HTTP fields (flat, not nested)

| Field | Type | Description |
|-------|------|-------------|
| `httpMethod` | string | GET, POST, etc. |
| `path` | string | Request path (sanitized, max 2048 chars) |
| `httpStatus` | number | Response status code |
| `clientIp` | string | Validated client IP |
| `durationMs` | number | Request duration in milliseconds |

> **Note:** Java uses flat top-level fields. Python uses a nested `http` object with snake_case keys (`method`, `path`, `status_code`, `client_ip`, `user_agent`, `duration_ms`). Both are valid — consumers should handle either shape.

### Error object

| Field | Type | Description |
|-------|------|-------------|
| `type` | string | Exception class/type name |
| `message` | string | Error message (sanitized) |
| `stackTrace` | string | Truncated stack trace (max 25 lines by default) |

### Method context (Java only)

| Field | Type | Description |
|-------|------|-------------|
| `className` | string | Fully qualified class name |
| `methodName` | string | Method name |

### Extra fields

| Field | Type | Description |
|-------|------|-------------|
| `extra` | object | Custom key-value pairs (max 20 fields, sanitized) |

## Event Types

| eventType | Description |
|-----------|-------------|
| `REQUEST_START` | HTTP request received (disabled by default) |
| `REQUEST_END` | HTTP request completed successfully |
| `REQUEST_ERROR` | HTTP request with error (5xx or exception) |
| `METHOD_SLOW` | Method exceeded duration threshold |
| `METHOD_ERROR` | Exception in intercepted method |
| `APPLICATION_ERROR` | Unhandled error |
| `APPLICATION_LOG` | Standard application log (Python only) |
| `SQL_SLOW` | SQL query exceeded threshold (when SQL monitoring enabled) |
| `SQL_ERROR` | SQL query error |

## Security Requirements (all languages)

1. **Sanitize sensitive fields**: `password`, `token`, `authorization`, `secret`, `apikey`, `accesstoken`, `refreshtoken`, `cardnumber`, `cvv`, `bearer`, `jwt`, `private_key`, `session`, `otp`, `mfa_code`, etc. — replace values with `***REDACTED***`
2. **Pattern matching**: fields containing `token`, `secret`, `password`, `credential`, or `auth` as substrings are treated as sensitive
3. **Input validation**: reject/strip control characters, CRLF injection, null bytes
4. **Truncation limits**: message 10K chars, path 2K chars, stack trace 25 lines, extra fields max 20 keys
5. **Deep redaction**: sanitize nested JSON bodies and maps recursively (with depth limit)
6. **No blocking I/O**: logging must never block the application
7. **Thread/async safety**: safe for concurrent use
8. **No network calls**: the library writes to stdout/stderr only — transport is not its job

## Implementation Status

| Language | Package | Status |
|----------|---------|--------|
| Java | `com.zerionis:zerionis-log-*` | Published (Maven Central) |
| Python | `zerionis-log` (PyPI) | Ready for publish |
| Node.js | `zerionis-log` (npm) | Planned |

## Repo Structure

```
zerionis-logs/              # Java (this repo)
  zerionis-log-core/        # Core logic, no Spring dependency
  zerionis-log-spring-boot2/# Spring Boot 2.x starter
  zerionis-log-spring-boot3/# Spring Boot 3.x starter

zerionis-log-python/        # Python (separate repo)
zerionis-log-node/          # Node.js (separate repo, planned)
```

Each language has its own repo, CI/CD, and README. All share this log contract.
