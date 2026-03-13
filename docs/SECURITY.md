# Zerionis Log — Security Hardening

This document describes the security measures implemented in zerionis-log to protect applications against common attack vectors related to logging.

---

## 1. Attack Vectors and Mitigations

### 1.1 Log Injection

**Risk:** An attacker injects newline characters (`\r\n`) into fields (headers, parameters) to forge fake log entries, confusing log analysis tools and SIEM systems.

**Mitigations:**
- **Jackson JSON serialization** automatically escapes `\n`, `\r`, `"`, and `\t` within string values, preventing line-break injection in JSON output.
- **`InputValidator.stripControlChars()`** removes control characters (except tab) from arbitrary string input before it reaches the log.
- **`InputValidator.validateExtraKey()`** rejects field keys containing control characters.

### 1.2 Log4Shell (CVE-2021-44228)

**Risk:** JNDI lookup injection via `${jndi:ldap://...}` patterns in log messages.

**Status:** **Not applicable.** Zerionis-log uses **Logback** (not Log4j2). Logback does not perform JNDI lookups on log message content. Additionally, `maven-enforcer-plugin` explicitly bans Log4j 2.x versions below 2.17.1 from the entire dependency tree to prevent accidental transitive inclusion.

### 1.3 Stored XSS via Log Dashboards

**Risk:** If log field values containing `<script>` tags are rendered in a web dashboard, they could execute malicious JavaScript.

**Mitigations:**
- **`InputValidator.stripHtml()`** removes HTML/XML tags from string values as defense-in-depth.
- **Dashboards should also escape output** (this is a secondary defense, not a replacement for proper output encoding).
- **Jackson JSON serialization** encodes special characters, providing an additional layer.

### 1.4 Trace ID Injection

**Risk:** Attackers send malicious values in the `X-Trace-Id` header (e.g., `"><script>alert(1)</script>`, SQL injection payloads, or extremely long strings).

**Mitigations:**
- **`InputValidator.validateTraceId()`** validates incoming trace IDs against a strict pattern: only `[a-zA-Z0-9._-]`, maximum 128 characters.
- Invalid trace IDs are silently discarded and a new UUID is generated internally.
- The original malicious header value never reaches the log output.

### 1.5 Client IP Spoofing / Injection

**Risk:** The `X-Forwarded-For` header is client-controlled. Attackers can inject arbitrary strings (SQL, scripts, garbage) disguised as IP addresses.

**Mitigations:**
- **`InputValidator.validateClientIp()`** validates extracted IPs against IPv4 and IPv6 format patterns.
- Invalid IPs are replaced with `"unknown"` instead of passing raw header content to logs.
- Maximum length: 45 characters (IPv6 maximum).

### 1.6 Payload Inflation / Denial of Service

**Risk:** Attackers trigger extremely long request paths, error messages, or extra field values to inflate log volume and exhaust storage.

**Mitigations:**
- **Request path:** Truncated to 2,048 characters (`InputValidator.truncatePath()`).
- **Log messages:** Truncated to 10,000 characters (`InputValidator.truncateMessage()`).
- **Stack traces:** Limited to configurable number of lines (default: 25, via `zerionis.log.max-stacktrace-lines`).
- **Extra field keys:** Maximum 128 characters, with control character validation.
- **Extra field values:** String values truncated to 4,096 characters (`InputValidator.truncateExtraValue()`).
- **Extra field count:** Maximum 20 fields per request (configurable via `zerionis.log.max-extra-fields`).

### 1.7 Sensitive Data Exposure

**Risk:** Developers accidentally log passwords, tokens, API keys, or credit card numbers in extra fields.

**Mitigations:**
- **`LogSanitizer`** automatically redacts values for fields matching a case-insensitive blacklist: `password`, `token`, `authorization`, `secret`, `apikey`, `accesstoken`, `refreshtoken`, `cardnumber`, `cvv`.
- **Custom fields** can be added via configuration: `zerionis.log.sanitize-fields: [ssn, dob, creditScore]`.
- Sanitization is applied in **two places**: the `ZerionisJsonLayout` (for application logs) and the `ZerionisRequestFilter` (for request events), ensuring no path bypasses redaction.
- **`LogSanitizer` is a Spring bean** (`@ConditionalOnMissingBean`), so applications can override it with a custom implementation.

### 1.8 ThreadLocal Memory Leaks

**Risk:** In thread-pooled environments (Tomcat, Jetty), ThreadLocal data from one request leaks into subsequent requests on the same thread, causing data corruption or memory exhaustion.

**Mitigations:**
- **`ZerionisContext.clear()`** calls both `map.clear()` and `ThreadLocal.remove()` to fully release the reference.
- **`ZerionisRequestFilter`** calls `MDC.clear()` and `ZerionisContext.clear()` in a `finally` block, guaranteeing cleanup even if the request throws an exception.

### 1.9 Supply Chain / Dependency Vulnerabilities

**Risk:** Transitive dependencies (Jackson, Logback, SLF4J) may contain known CVEs.

**Mitigations:**
- **`maven-enforcer-plugin`** runs on every build:
  - Enforces dependency convergence (no version conflicts).
  - Bans known-vulnerable Log4j versions from the dependency tree.
  - Requires Maven 3.6+ and Java 11+.
- **`ossindex-maven-plugin`** scans all dependencies against Sonatype OSS Index for known CVEs on every build (phase: `validate`).
- **Dependency versions are managed** per module: core pins its own versions, Spring Boot modules inherit from their respective BOM imports, preventing cross-version conflicts.
- Run manual audit anytime: `mvn ossindex:audit`.

---

## 2. Security Configuration Reference

| Property | Default | Description |
|---|---|---|
| `zerionis.log.sanitize-enabled` | `true` | Enable/disable sensitive field redaction |
| `zerionis.log.sanitize-fields` | `[]` | Additional field names to redact (added to built-in list) |
| `zerionis.log.max-extra-fields` | `20` | Maximum extra fields per request |
| `zerionis.log.max-stacktrace-lines` | `25` | Maximum stack trace lines in error logs |
| `zerionis.log.exclude-endpoints` | `[]` | Endpoints excluded from logging (e.g. health checks) |

---

## 3. What Zerionis Log Does NOT Protect Against

- **Application-level vulnerabilities** (SQL injection, command injection, etc.) — these are the developer's responsibility.
- **Log storage security** — encrypting logs at rest, access control to log aggregators (CloudWatch, ELK, etc.) is infrastructure responsibility.
- **Network-level attacks** — TLS termination, DDoS protection, etc.
- **Tampered dependencies** — we enforce strict checksums (see 1.10) but don't verify GPG signatures of individual artifacts.

---

## 4. Zero-Day Supply Chain Hardening

### 1.10 Strict Checksum Verification

**Risk:** A compromised Maven mirror or MITM attack serves tampered JARs with injected malicious code.

**Mitigations:**
- **`.mvn/maven.config`** enforces `--strict-checksums` globally. Maven will **fail the build** if any downloaded artifact's checksum doesn't match what Maven Central published.
- This runs on every `mvn` invocation without developer action.

### 1.11 Software Bill of Materials (SBOM)

**Risk:** A new zero-day CVE is announced (like Log4Shell). You need to know in seconds whether any version of the affected library is in your dependency tree.

**Mitigations:**
- **CycloneDX Maven plugin** generates `target/bom.json` on every `mvn package`.
- The SBOM lists every direct and transitive dependency with exact versions.
- Compatible with OWASP Dependency-Track, Grype, and other scanning tools.
- Run manually: `mvn cyclonedx:makeAggregateBom`.

### 1.12 Reproducible Builds

**Risk:** A compromised build environment injects code during compilation (build-time supply chain attack). Without reproducible builds, two compilations of identical source produce different binaries, making tampering undetectable.

**Mitigations:**
- **`project.build.outputTimestamp`** is set to a fixed value, stripping non-deterministic metadata (timestamps, OS info) from JAR manifests.
- **`maven-jar-plugin`** configured to produce deterministic output.
- Two builds of the same Git commit produce byte-identical artifacts, allowing integrity verification.

### 1.13 Security Test Suite

**Risk:** Validators have edge cases that allow bypass (e.g., unicode homographs, null bytes, nested payloads).

**Mitigations:**
- **56 security-focused tests** in `InputValidatorSecurityTest` and `LogSanitizerSecurityTest`.
- Tests simulate real attack payloads: JNDI injection, SQL injection, XSS vectors, CRLF injection, command injection, unicode homograph attacks, null byte injection, buffer overflow attempts, and DoS via oversized inputs.
- Tests run on every build via `maven-surefire-plugin`.

---

## 5. Security Utilities Reference

### `InputValidator` (core module)

| Method | Purpose |
|---|---|
| `validateTraceId(String)` | Accepts only `[a-zA-Z0-9._-]{1,128}`, returns null if invalid |
| `truncatePath(String)` | Limits request paths to 2,048 characters |
| `truncateMessage(String)` | Limits log messages to 10,000 characters |
| `validateExtraKey(String)` | Rejects null, empty, control chars; max 128 chars |
| `truncateExtraValue(Object)` | Truncates String values to 4,096 characters |
| `validateClientIp(String)` | Validates IPv4/IPv6 format, returns "unknown" if invalid |
| `stripHtml(String)` | Removes HTML/XML tags |
| `stripControlChars(String)` | Removes control characters (except tab) |

### `LogSanitizer` (core module)

| Method | Purpose |
|---|---|
| `sanitize(Map)` | Returns copy with sensitive values replaced by `***REDACTED***` |
| `isSensitive(String)` | Checks if a field name is in the blacklist |

---

## 6. Reporting Security Issues

If you discover a security vulnerability in zerionis-log, please report it privately via GitHub Security Advisories or email. Do not open a public issue.
