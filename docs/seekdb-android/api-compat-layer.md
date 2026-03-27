# SQLite Compatibility Layer Design

## 1. Objective

Provide Android SQLite-style interface and behavior compatibility for application code and Room integration, while executing on SeekDB under the hood.

This layer targets interface and behavioral compatibility, not SQLite C ABI compatibility.

## 2. API Scope

Compatibility layer exposes behavior equivalent to commonly used Android SQLite abstractions:

- Database open/close and health checks.
- SQL execution (`execSQL` style).
- Query execution (`rawQuery` style).
- Prepared statements and bind operations.
- Transaction workflow.
- Cursor-based result traversal.
- SQLite-like exception semantics.

## 3. Room-facing Contract

The layer must satisfy Room's expected database backend semantics through a SupportSQLite-compatible path:

- Open helper lifecycle hooks.
- Compile statement and bind lifecycle.
- Query cursor behavior.
- Transaction begin/success/end patterns.
- Predictable error propagation.

## 4. Internal Mapping Rules

### 4.1 Connection

- Compat open path initializes runtime and obtains connection via native core.
- Close path releases statement caches, active cursors, then native connection.

### 4.2 Statement and Binding

- Compat bind methods map typed values to `SeekdbValue`.
- Index conversion handled in compat layer to avoid leaking index convention differences.
- Rebind and clear semantics follow Android expectations.

### 4.3 Query and Cursor

- Query returns cursor abstraction over `SeekdbResult`.
- Cursor navigation (`moveToFirst`, `moveToNext`, position checks) maps to result traversal.
- Column metadata methods are backed by native column introspection.

### 4.4 Transactions

- Begin-success-end pattern is mapped to core transaction primitives.
- If success marker is not set, end performs rollback semantics.
- Nested behavior is explicitly documented (support level declared in matrix).

## 5. Exception Model

Compat layer throws Android SQLite-style exceptions.

Mapping inputs:

- seekdb return codes.
- statement/connection error messages.
- SQLSTATE when available.

Mapping result:

- Deterministic exception class and message policy.
- No raw native code leakage to app-facing API unless debug mode is enabled.

## 6. Compatibility Boundaries

### Must be compatible

- CRUD statements and common DDL.
- Prepared statement bind and execution basics.
- Core transaction semantics.
- Cursor typed read semantics.

### Can degrade (documented)

- Some advanced SQLite pragmas and edge-case metadata behavior.
- Specific lock/busy timing details.

### Not supported initially

- SQLite extension loading.
- Features coupled to SQLite internal VM behavior.

## 7. Performance Notes

- Statement caching in compat layer to reduce JNI and prepare overhead.
- Cursor read path should avoid per-cell JNI overhead where possible.
- Use batched extraction strategy for large result sets when compatible with cursor semantics.

## 8. Test Requirements

- Contract tests for every must-compatible API.
- Behavior tests for transaction rollback/commit paths.
- Cursor null/empty string/type conversion tests.
- Room smoke and migration tests under instrumentation.
