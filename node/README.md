# zerionis-log-node

Structured JSON logging for Node.js applications (Express, NestJS, Fastify).

Port of [zerionis-log](https://github.com/zerionis-dev/zerionis-logs) for the Node.js ecosystem.

## Status

**Not yet implemented.** See `SPEC.md` for implementation plan.

## Goals

- Same JSON log contract as the Java library
- Zero-config: add dependency, import, done
- Framework integrations: Express middleware, NestJS interceptor, Fastify plugin
- Automatic request correlation (traceId, requestId)
- Sensitive field redaction
- Non-blocking by design
- Publish to npm as `zerionis-log`
