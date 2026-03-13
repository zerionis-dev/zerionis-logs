# zerionis-log

**Logging estructurado automático para Spring Boot.**

Agrega una dependencia Maven y todos tus logs se convierten en JSON estructurado con traceId, contexto HTTP, timing y sanitización de datos sensibles. Sin tocar tu código.

---

## El problema

```java
// Logs típicos en muchos proyectos
log.error("Error en proceso");
log.info("Entró al método");
catch (Exception e) { e.printStackTrace(); }
```

Logs sin estructura, sin contexto, sin correlación. Cuando algo falla en producción, cuesta encontrar la causa.

## La solución

```xml
<dependency>
    <groupId>com.zerionis</groupId>
    <artifactId>zerionis-log-spring-boot3</artifactId>
    <version>1.0.0</version>
</dependency>
```

Cero configuración obligatoria. Se activa automáticamente.

**Antes:**
```
ERROR c.e.PaymentService - Error en proceso
```

**Después:**
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

## Instalación

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

## Qué hace automáticamente

| Feature | Cómo lo hace |
|---------|-------------|
| JSON estructurado | Layout custom de Logback que reemplaza el formato por defecto |
| traceId por request | UUID v4 generado automáticamente, compatible con Sleuth/Micrometer |
| Contexto HTTP | Servlet Filter que captura path, método, IP, requestId |
| Timing de requests | Duración medida en el Filter |
| Captura de excepciones | ControllerAdvice + AOP |
| Detección de métodos lentos | AOP Aspect con umbral configurable |
| Sanitización de datos sensibles | Detección automática de campos password, token, secret, etc. |

---

## Configuración

Todo tiene valores por defecto. Solo configura lo que necesites:

```yaml
zerionis:
  log:
    service-name: payments-api          # Nombre del servicio (o usa spring.application.name)
    environment: production             # Ambiente
    version: 2.1.8                      # Versión del servicio
    request-start-enabled: false        # Loggear inicio de request (default: false)
    max-stacktrace-lines: 25           # Líneas de stackTrace (default: 25)
    max-extra-fields: 20               # Campos extra máximos (default: 20)
    slow-method-threshold-ms: 1000     # Umbral de método lento en ms (default: 1000)
    sanitize-enabled: true             # Sanitizar campos sensibles (default: true)
    exclude-endpoints:                 # Endpoints a excluir del logging
      - /actuator/health
      - /favicon.ico
```

---

## Campos extra

Agrega contexto adicional a tus logs:

```java
ZerionisContext.put("orderId", "ORD-123");
ZerionisContext.put("provider", "conekta");
ZerionisContext.put("customerId", "C001");
```

Se incluyen automáticamente en el campo `extra` del JSON:

```json
{
  "extra": {
    "orderId": "ORD-123",
    "provider": "conekta",
    "customerId": "C001"
  }
}
```

El contexto se limpia automáticamente al terminar cada request.

---

## Taxonomía de eventos

| eventType | Descripción |
|-----------|-------------|
| `REQUEST_END` | Request HTTP completado |
| `REQUEST_ERROR` | Request HTTP con error |
| `METHOD_SLOW` | Método que superó el umbral de duración |
| `METHOD_ERROR` | Excepción en método interceptado |
| `APPLICATION_ERROR` | Error no manejado |
| `REQUEST_START` | Inicio de request (desactivado por defecto) |

---

## Arquitectura del proyecto

```
zerionis-log/
├── zerionis-log-core/              → Lógica pura, sin Spring
│   ├── model/                      → ZerionisLogEvent, ZerionisError, EventType
│   ├── context/                    → ZerionisContext (ThreadLocal API)
│   ├── util/                       → TraceId, RequestId, StackTrace, Sanitizer
│   └── format/                     → JSON Layout, Formatter, Field names
│
├── zerionis-log-spring-boot2/      → Starter para Spring Boot 2.7.x (javax.servlet)
└── zerionis-log-spring-boot3/      → Starter para Spring Boot 3.x (jakarta.servlet)
```

---

## Compatibilidad

| Versión | Spring Boot | Java |
|---------|------------|------|
| `zerionis-log-spring-boot2` | 2.7.x | 11+ |
| `zerionis-log-spring-boot3` | 3.x | 17+ |

---

## Licencia

Apache 2.0
