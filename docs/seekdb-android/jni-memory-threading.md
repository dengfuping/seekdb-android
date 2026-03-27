# JNI Memory and Threading Contract

## 1. Handle Ownership Rules

- `SeekdbConnection` is owned by connection wrapper and released exactly once.
- `SeekdbStmt` is owned by statement wrapper and released on close/reset policy.
- `SeekdbResult` is owned by query/execute wrapper and released after cursor/result close.
- `SeekdbValue` is owned by bind/value wrapper and released after bind lifecycle or explicit close.

## 2. Safety Invariants

- No JNI method returns raw pointers to app layer.
- Wrapper close methods are idempotent.
- Native release is guarded by atomic closed state.
- Any close failure is surfaced as warning-level telemetry and must not crash app close path.

## 3. Borrowed Lifetime Objects

- Row handles are borrowed from current result and invalid after:
  - next row advancement that invalidates pointer contract.
  - result free.
- Compat cursor must not cache borrowed row pointers beyond valid window.

## 4. Threading Rules

- Engine open/close are process-level guarded operations.
- A connection handle is single-thread affinity by default.
- Statement handle inherits connection affinity.
- Cross-thread handoff is unsupported unless explicit synchronization wrapper is implemented.

## 5. Error Channel Rules

- Prefer operation return code and scoped error APIs over thread-local global error APIs.
- Thread-local error APIs are diagnostic fallback only.
- All JNI boundaries must convert native errors to deterministic Java/Kotlin error forms.

## 6. JNI Boundary Patterns

- Validate all pointers and indexes at boundary entry.
- Fail fast on closed handle access.
- Convert Java strings/blobs with explicit encoding/length control.
- Avoid repeated JNI lookups in hot paths by caching class/method ids during init.

## 7. Verification Checklist

- Double-close tests for every handle type.
- Use-after-close tests for connection/statement/result/value.
- Cross-thread misuse tests showing deterministic failure.
- Leak checks in instrumentation and stress tests.
