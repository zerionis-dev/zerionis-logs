# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.2] - 2026-04-09

### Added
- Distributed trace propagation — RestTemplate interceptor automatically injects `X-Trace-Id` and `X-Request-Id` headers on outbound HTTP calls
- Async context propagation — TaskDecorator propagates MDC and ZerionisContext to `@Async` threads
- `ZerionisContext.setAll()` API for bulk context restoration across thread boundaries
- 20 new tests (4 core + 8 boot3 + 8 boot2) covering trace propagation and async context

### Fixed
- `userId` always null — was extracted before Spring Security filter chain ran; now extracted post-chain
- `copyBodyToResponse()` could swallow the original exception when failing inside `finally`
- `stripControlChars` did not filter DEL character (0x7F), inconsistent with `validateExtraKey`
- Reflection method handles for Spring Security (`getAuthentication`, `isAuthenticated`, `getName`) now cached instead of resolved per request
- `StackTraceUtils` no longer captures full stack trace in memory before truncating — uses `LineLimitedPrintWriter` to stop early

### Removed
- Dead `formatter` field from `ZerionisRequestFilter`, `ZerionisMethodAspect`, `ZerionisStatementInterceptor`, and related classes — was injected but never used
- Unused `ERROR_MESSAGE` constant from `JsonFieldNames`

### Changed
- CI workflow now triggers on both `main` and `master` branches
- SPEC.md corrected "GitHub Packages" → "Maven Central"

## [1.0.3] - 2026-04-07

### Added
- Deep JSON body redaction — sanitizes sensitive fields inside request/response bodies
- Header capture and sanitization — automatically redacts Authorization, Cookie, etc.
- Content-type filtering — skips binary content from body capture
- Partial redaction mode — shows first4...last4 for long sensitive values
- Expanded default blacklist — passwd, bearer, jwt, private_key, session, otp, mfa_code, etc.
- Pattern-based field matching — detects sensitive fields by substring (token, secret, password, credential, auth)
- 116 unit tests for core module covering sanitizer, validators, model, formatter, and context

### Fixed
- Field pattern matching refined to avoid false positives (e.g. "key" alone no longer triggers redaction)

### Changed
- POM dependency management cleaned up, JUnit versions aligned across modules

## [1.0.2] - 2026-03-12

### Fixed
- Dependency scopes: slf4j, logback, jackson, spring-boot-autoconfigure changed to `provided` to prevent transitive version conflicts
- Added `@ConditionalOnClass` guard on AOP aspect bean to prevent `ClassNotFoundException` without `spring-boot-starter-aop`

### Changed
- README rewritten in English with badges, quick start guide, security section, and contributing guidelines
- Removed duplicate `README_EN.md`
- Added `CHANGELOG.md`

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

[1.1.2]: https://github.com/zerionis-dev/zerionis-logs/compare/v1.0.3...v1.1.2
[1.0.3]: https://github.com/zerionis-dev/zerionis-logs/compare/v1.0.2...v1.0.3
[1.0.2]: https://github.com/zerionis-dev/zerionis-logs/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/zerionis-dev/zerionis-logs/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/zerionis-dev/zerionis-logs/releases/tag/v1.0.0
