# Zerionis Log — Especificación Técnica v1.0

## Decisiones base

| Decisión | Resultado |
|----------|-----------|
| Spring Boot | 2.7.x + 3.x |
| Estrategia multi-versión | Multi-módulo (core + spring-boot2 + spring-boot3) |
| Logging framework | Logback only |
| TraceId | Propio (UUID), compatible con Sleuth/Micrometer si ya existe |
| Convención JSON | camelCase |
| Java mínimo | 11 |
| Publicación | Maven Central + GitHub Packages, repo público |
| Licencia | Open source (Apache 2.0) |

---

## Estructura de módulos

```
zerionis-log/
├── zerionis-log-core/                → Lógica independiente de Spring
├── zerionis-log-spring-boot2/        → Starter para Spring Boot 2.7.x
├── zerionis-log-spring-boot3/        → Starter para Spring Boot 3.x
└── pom.xml                           → Parent POM
```

Cada módulo spring funciona directamente como starter. El usuario solo instala uno:

```xml
<dependency>
    <groupId>com.zerionis</groupId>
    <artifactId>zerionis-log-spring-boot3</artifactId>
    <version>1.0.2</version>
</dependency>
```

---

## Responsabilidades por módulo

### zerionis-log-core

Lógica independiente de Spring. No debe depender de Spring, HttpServletRequest, ControllerAdvice ni anotaciones Spring.

Clases:

- `ZerionisLogEvent` — modelo del evento de log
- `ZerionisError` — modelo del error (type, message, stackTrace, truncated)
- `ZerionisContext` — API pública para campos extra (ThreadLocal)
- `TraceIdGenerator` — genera UUID para traceId
- `RequestIdGenerator` — genera requestId
- `LogSanitizer` — blacklist de campos sensibles
- `StackTraceUtils` — truncar stacktrace a N líneas
- `ZerionisLogFormatter` — serialización JSON del evento
- `JsonFieldNames` — constantes de nombres de campos
- `EventType` — enum de tipos de evento

### zerionis-log-spring-boot2

Integración con Spring Boot 2.7.x:

- `ZerionisAutoConfiguration` — registrada en `META-INF/spring.factories`
- `ZerionisRequestFilter` — Servlet Filter (`javax.servlet`)
- `ZerionisMethodAspect` — AOP Aspect
- `ZerionisExceptionHandler` — `@RestControllerAdvice`
- `ZerionisProperties` — configuración vía `application.yml`

### zerionis-log-spring-boot3

Integración con Spring Boot 3.x:

- `ZerionisAutoConfiguration` — registrada en `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `ZerionisRequestFilter` — Servlet Filter (`jakarta.servlet`)
- `ZerionisMethodAspect` — AOP Aspect
- `ZerionisExceptionHandler` — `@RestControllerAdvice`
- `ZerionisProperties` — configuración vía `application.yml`

Diferencias con spring-boot2: `javax` → `jakarta`, mecanismo de auto-configuration.

---

## Componentes y responsabilidades

### Servlet Filter (`ZerionisRequestFilter`)

Responsable de:
- Generar `traceId` (UUID) si no existe en headers
- Generar `requestId`
- Capturar `path`, `httpMethod`, `clientIp`
- Medir duración del request
- Emitir log final del request

El Filter es la **única fuente** que emite:
- `REQUEST_END`
- `REQUEST_ERROR`

Si `REQUEST_START` está habilitado, también lo emite el Filter.

### ControllerAdvice (`ZerionisExceptionHandler`)

Responsable de:
- Capturar excepciones no manejadas
- Enriquecer contexto del error (tipo, mensaje, stacktrace)
- Guardar información en contexto para que el Filter la use

**No emite log final.** El Filter lo hace. Esto evita logs duplicados.

### AOP Aspect (`ZerionisMethodAspect`)

Responsable de emitir:
- `METHOD_ERROR` — excepción en método interceptado
- `METHOD_SLOW` — método que supera umbral de duración

Intercepta por defecto:
- `@RestController`
- Métodos anotados explícitamente

**No intercepta** por defecto:
- Todos los `@Service`
- Todos los `@Repository`

---

## Taxonomía de eventos (eventType)

| eventType | Quién lo emite | Descripción |
|-----------|---------------|-------------|
| `REQUEST_START` | Filter | Inicio de request HTTP (desactivado por defecto) |
| `REQUEST_END` | Filter | Request HTTP completado |
| `REQUEST_ERROR` | Filter | Request HTTP con error |
| `METHOD_SLOW` | AOP Aspect | Método superó umbral de duración |
| `METHOD_ERROR` | AOP Aspect | Excepción en método interceptado |
| `APPLICATION_ERROR` | JSON Layout | Error de aplicación (fuera de contexto HTTP) |
| `SQL_SLOW` | SQL Interceptor | Query SQL superó umbral de duración |
| `SQL_ERROR` | SQL Interceptor | Query SQL con error |

`REQUEST_START` está desactivado por defecto para no duplicar volumen de logs.
`SQL_SLOW` y `SQL_ERROR` requieren `zerionis.log.sql-enabled=true`.

---

## Formato JSON del log

```json
{
  "timestamp": "2026-03-10T14:32:01.123Z",
  "level": "ERROR",
  "eventType": "REQUEST_ERROR",
  "service": "payments-api",
  "environment": "production",
  "version": "2.1.8",
  "traceId": "550e8400-e29b-41d4-a716-446655440000",
  "requestId": "req-a1b2c3",
  "path": "/payments/charge",
  "httpMethod": "POST",
  "httpStatus": 500,
  "clientIp": "10.0.1.54",
  "userId": null,
  "className": "com.example.PaymentService",
  "methodName": "processCharge",
  "durationMs": 2340,
  "message": "Error processing payment charge",
  "error": {
    "type": "RedisConnectionException",
    "message": "Connection timeout after 2000ms",
    "stackTrace": "line1\nline2\nline3...",
    "stackTraceLines": 25,
    "stackTraceTruncated": true
  },
  "extra": {
    "orderId": "123",
    "provider": "conekta"
  }
}
```

### Reglas del formato

- `timestamp` — ISO 8601 con milisegundos, siempre UTC
- `level` — TRACE, DEBUG, INFO, WARN, ERROR
- `service` — desde `zerionis.log.service-name` o `spring.application.name`
- `environment` — desde `zerionis.log.environment` o `spring.profiles.active`
- `version` — desde `zerionis.log.version`
- `traceId` — UUID propio, o reutiliza Sleuth/Micrometer si existe
- `requestId` — generado por el Filter, diferente del traceId
- `path` y `httpMethod` — separados, nunca combinados
- `httpStatus` — nullable, solo disponible en `REQUEST_END` y `REQUEST_ERROR`
- `error` — nullable, solo presente cuando hay excepción
- `extra` — Map libre con límites (max campos, sanitización)
- Todos los nombres en camelCase

---

## Campo extra — API pública

```java
ZerionisContext.put("orderId", "123");
ZerionisContext.put("provider", "conekta");
ZerionisContext.get("orderId");
ZerionisContext.remove("orderId");
ZerionisContext.clear();
```

Implementación interna: `ThreadLocal<Map<String, Object>>`

Se limpia automáticamente al terminar el request (en el Filter).

Restricciones:
- Máximo N campos (configurable, default 20)
- Sanitización automática de campos en blacklist
- Tamaño limitado por valor

---

## StackTrace

Truncado por defecto a 25 líneas. Configurable.

```json
"error": {
  "type": "RedisConnectionException",
  "message": "Connection timeout after 2000ms",
  "stackTrace": "line1\nline2\n...",
  "stackTraceLines": 25,
  "stackTraceTruncated": true
}
```

---

## Sanitización

Blacklist de campos sensibles (aplicada a `extra` y request/response body si está habilitado):

- password
- token
- authorization
- secret
- apiKey
- accessToken
- refreshToken
- cardNumber
- cvv

Los campos detectados se enmascaran automáticamente.

---

## Configuración

```yaml
zerionis:
  log:
    service-name: payments-api
    environment: production
    version: 2.1.8
    request-start-enabled: false
    include-request-body: false
    include-response-body: false
    max-stacktrace-lines: 25
    max-extra-fields: 20
    slow-method-threshold-ms: 1000
    sanitize-enabled: true
    sanitize-fields:
      - password
      - token
      - secret
      - authorization
      - apiKey
      - accessToken
      - refreshToken
      - cardNumber
      - cvv
    exclude-endpoints:
      - /actuator/health
      - /favicon.ico
    sql-enabled: false
    sql-slow-threshold-ms: 500
```

Todos los valores tienen defaults sensatos. Cero configuración obligatoria.

---

## Alcance v1.0

### Incluido
- Auto-configuration (se activa sola)
- Logback JSON appender (formato estándar)
- Servlet Filter → MDC automático (traceId, requestId, path, httpMethod, clientIp)
- AOP en `@RestController` (timing + errores)
- ControllerAdvice (excepciones no manejadas)
- ZerionisContext (campos extra)
- Sanitización de campos sensibles
- StackTrace truncado
- Configuración vía `application.yml`

### Implementado (desde v1.0.0)
- Interceptor SQL (queries lentas y errores, opt-in via `sql-enabled`)
- Request/response body capture (opt-in via `include-request-body` / `include-response-body`)
- Anotaciones: `@LogIgnore` (class/method), `@LogSkip` (method), `@LogDetail` (method)
- userId desde SecurityContext (via reflection, sin dependencia directa a spring-security)

### Diferido
- Soporte Log4j2

---

## Flujo completo

```
Microservicio con Spring Boot
        ↓
zerionis-log-spring-boot3 (o boot2)
        ↓
Logs JSON estructurados (stdout / file / CloudWatch)
        ↓
Almacenamiento (CloudWatch / OpenSearch / S3 / Kafka)
        ↓
Análisis y diagnóstico
```
