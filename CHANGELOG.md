# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.1] - 2026-03-12

### Changed
- Updated project metadata and documentation

## [1.0.0] - 2026-03-12

### Added
- Structured JSON logging via custom Logback layout
- Auto-configuration for Spring Boot 2.7.x (`javax.servlet`) and 3.x (`jakarta.servlet`)
- Servlet Filter with automatic MDC enrichment: `traceId`, `requestId`, `path`, `httpMethod`, `clientIp`
- AOP Aspect for `@RestController` methods: slow method detection and error capture
- `@RestControllerAdvice` for unhandled exception logging
- `ZerionisContext` ThreadLocal API for custom extra fields
- Sensitive data sanitization (password, token, secret, apiKey, etc.)
- Configurable stack trace truncation (default: 25 lines)
- Input validation and security hardening (XSS, CRLF, log injection, trace ID injection)
- Full configuration via `application.yml` under `zerionis.log.*`
- 56 security-focused tests (InputValidator, LogSanitizer)
- Integration tests for both Spring Boot 2 and 3 modules
- Supply chain hardening: strict checksums, CycloneDX SBOM, reproducible builds
- Published to Maven Central under `com.zerionis`

[1.0.1]: https://github.com/zerionis-dev/zerionis-logs/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/zerionis-dev/zerionis-logs/releases/tag/v1.0.0
