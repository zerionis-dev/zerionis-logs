# zerionis-log — Multi-language Spec

All language implementations MUST produce the same JSON log contract.

## Log Contract (v1)

Every log event is a single JSON line with these fields:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `timestamp` | ISO 8601 | yes | UTC, e.g. `2026-03-13T10:30:00.123Z` |
| `level` | string | yes | `DEBUG`, `INFO`, `WARN`, `ERROR` |
| `logger` | string | yes | Logger name (class, module, file) |
| `message` | string | yes | Log message (sanitized, max 10,000 chars) |
| `service` | string | yes | Application name |
| `environment` | string | no | `production`, `staging`, `development` |
| `version` | string | no | Application version |
| `traceId` | string | no | Distributed trace ID (UUID v4 or propagated) |
| `requestId` | string | no | Per-request correlation ID |
| `eventType` | string | no | `REQUEST_END`, `REQUEST_ERROR`, `METHOD_SLOW`, `METHOD_ERROR`, `APPLICATION_ERROR` |
| `http` | object | no | HTTP context (see below) |
| `error` | object | no | Error details (see below) |
| `duration` | number | no | Duration in milliseconds |
| `extra` | object | no | Custom key-value pairs (max 20 fields) |

### HTTP object

| Field | Type | Description |
|-------|------|-------------|
| `method` | string | GET, POST, etc. |
| `path` | string | Request path (sanitized) |
| `statusCode` | number | Response status code |
| `clientIp` | string | Validated client IP |
| `userAgent` | string | Truncated User-Agent |

### Error object

| Field | Type | Description |
|-------|------|-------------|
| `type` | string | Exception class/type name |
| `message` | string | Error message (sanitized) |
| `stackTrace` | string | Truncated stack trace (max 25 lines) |

## Security Requirements (all languages)

1. **Sanitize sensitive fields**: `password`, `token`, `authorization`, `secret`, `apikey`, `accesstoken`, `refreshtoken`, `cardnumber`, `cvv` — replace values with `[REDACTED]`
2. **Input validation**: reject/strip control characters, CRLF injection, null bytes
3. **Truncation limits**: message 10K chars, stack trace 25 lines, extra fields max 20 keys
4. **No blocking I/O**: logging must never block the application
5. **Thread/async safety**: safe for concurrent use
6. **No network calls**: the library writes to stdout/stderr only — transport is not its job

## Implementation Priorities

1. **Python** (PyPI: `zerionis-log`) — FastAPI, Django, Flask
2. **Node.js** (npm: `zerionis-log`) — Express, NestJS, Fastify
3. **.NET** (NuGet: `Zerionis.Log`) — ASP.NET Core (future, on demand)

## Repo Structure

```
zerionis-logs/
  zerionis-log-core/          # Java: core (no Spring dependency)
  zerionis-log-spring-boot2/  # Java: Spring Boot 2.x starter
  zerionis-log-spring-boot3/  # Java: Spring Boot 3.x starter
  python/                     # Python: zerionis-log-python
  node/                       # Node.js: zerionis-log-node
  SPEC.md                     # This file (shared contract)
  pom.xml                     # Java aggregator (does not affect python/node)
```

Each language has its own CI/CD workflow, publishing pipeline, and README.
All share the same log contract defined in this document.
