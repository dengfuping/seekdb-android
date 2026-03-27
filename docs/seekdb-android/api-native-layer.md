# SeekDB Native Layer Design

## 1. Objective

Expose a direct, typed Android API over SeekDB C ABI for advanced usage and as the shared foundation used by the compatibility layer.

## 2. Design Principles

- Keep wrappers thin and explicit.
- Preserve ownership boundaries from C ABI.
- Avoid hidden behavior divergence from `seekdb_*` semantics.
- Provide Kotlin-friendly and Java-friendly signatures.

## 3. Core API Groups

- Runtime lifecycle:
  - open/close engine.
  - connect/disconnect.
- Direct query:
  - query with byte-length and c-string convenience path.
- Result reading:
  - row iteration, column metadata, typed getters.
- Prepared statements:
  - prepare, bind value, execute, fetch, reset, close.
- Value objects:
  - allocate/free, set/get typed value.
- Transactions:
  - begin, commit, rollback, autocommit.
- Error inspection:
  - code/message/sqlstate for connection and statement scopes.

## 4. Proposed Public Types

- `SeekdbClient`
- `SeekdbConnectionHandle`
- `SeekdbStatement`
- `SeekdbResultSet`
- `SeekdbRowView`
- `SeekdbValue`
- `SeekdbException` (or sealed error model)

## 5. Lifecycle Contract

- Every native-owned handle has deterministic `close()`.
- Releasing order:
  - statement/result/value before connection.
- Safe idempotent close is required on all closeable wrappers.
- Use-after-close must fail fast with clear error.

## 6. Threading Contract

- Connection and statement handles are not shared across threads by default.
- If concurrent usage is required, clients use separate connections.
- Native layer exposes explicit docs for thread affinity and safe usage patterns.

## 7. Data Type Semantics

- Map `SeekdbTypeId` to typed getters/setters.
- Preserve NULL semantics and expose explicit null checks.
- Distinguish empty string from NULL in API contract.
- Keep binary/string encoding behavior explicit (UTF-8 assumption where applicable).

## 8. Error Surface

- All failures include:
  - numeric code.
  - descriptive message.
  - sqlState when available.
- Error object can include operation and SQL context with sanitization policy.

## 9. Relationship With Compat Layer

- Native layer is independently usable.
- Compat layer uses same execution core and native layer abstractions where practical.
- Shared code lives in `core` package to avoid duplication.

## 10. Test Requirements

- Lifecycle and close-idempotence tests.
- Typed bind/get roundtrip tests.
- Error propagation and sqlState tests.
- Concurrency misuse tests (expected failures documented).
