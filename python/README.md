# zerionis-log-python

Structured JSON logging for Python applications (FastAPI, Django, Flask).

Port of [zerionis-log](https://github.com/zerionis-dev/zerionis-logs) for the Python ecosystem.

## Status

**Not yet implemented.** See `SPEC.md` for implementation plan.

## Goals

- Same JSON log contract as the Java library
- Zero-config: add dependency, import, done
- Framework integrations: FastAPI middleware, Django middleware, Flask extension
- Automatic request correlation (traceId, requestId)
- Sensitive field redaction
- No blocking I/O
- Publish to PyPI as `zerionis-log`
