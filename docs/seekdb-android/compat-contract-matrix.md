# SQLite Compatibility Contract Matrix

## 1. Contract Levels

- MustCompatible: required for Room P0 and mainstream app migration.
- DegradedCompatible: supported with documented behavior differences.
- NotSupported: out of scope for current phases.

## 2. API and Behavior Matrix

- Open/close database lifecycle: MustCompatible
  - Contract: deterministic open state, close safety, repeated close tolerated.
- Execute SQL without rowset (`execSQL` style): MustCompatible
  - Contract: DDL/DML execution and error propagation.
- Query and cursor traversal (`rawQuery` style): MustCompatible
  - Contract: position semantics, typed reads, null handling.
  - Current: default path materializes into `Object[][]` + `MatrixCursor`. Optional path: `SeekdbStreamingPolicy` / `SeekdbSQLite.setStreamingQueryCursorsEnabled(true)` returns `SeekdbWindowedCursor` (`AbstractWindowedCursor` + `CursorWindow`, lazy `nativeResultReadNextRowTyped` into a row buffer and window chunks).
  - **Memory (DegradedCompatible):** default remains full materialization for Room compatibility; windowed path bounds `CursorWindow` size but still grows a Java row buffer as the native scan advances (same forward-only engine constraint as streaming). True sqlite-android-style reposition without buffering requires native window fill or scrollable cursors.
- Prepared statement compile/bind/execute: MustCompatible
  - Contract: bind null/int/long/double/string/blob semantics and reuse workflow.
  - BLOB bind: uses `seekdb_value_set_blob` when exported by `libseekdb.so`; otherwise binary-safe `seekdb_value_set_string` with raw byte length (documented fallback).
- Transaction begin/success/end: MustCompatible
  - Contract: success flag controls commit vs rollback behavior.
  - `SQLiteTransactionListener`: `onBegin` at start; `onCommit` after successful commit; `onRollback` after rollback path or when no usable connection.
- Nested `beginTransaction` / `endTransaction` (depth refcount): MustCompatible
  - Contract: only the **outermost** `endTransaction` maps to native commit/rollback; inner pairs increment/decrement depth without a second native `BEGIN` (Room / `InvalidationTracker` can nest around app transactions). Not SQLite savepoints.
- Busy/lock timing parity: DegradedCompatible
  - Contract: expose stable error behavior, timing details may differ.
- `isDbLockedByCurrentThread`: DegradedCompatible
  - Contract: approximated as `inTransaction()`; not a file lock probe.
- `getMaximumSize` / `setMaximumSize`: NotSupported (explicit)
  - Contract: throws `UnsupportedOperationException` with message (avoid silent bogus limits).
- `getAttachedDbs`: MustCompatible
  - Contract: returns an empty list (no ATTACH support in this phase).
- `isDatabaseIntegrityOk`: DegradedCompatible
  - Contract: returns `false` until `PRAGMA integrity_check` (or equivalent) is wired; means “not verified”, not “corrupt”.
- SQLite pragma full parity: DegradedCompatible
  - Contract: support critical pragmas only; others documented.
  - `PRAGMA foreign_keys` is issued when `setForeignKeyConstraintsEnabled` is called and a connection exists.
- SQLite extension loading: NotSupported
  - Contract: explicitly unavailable.
- SQLite VM internal semantics: NotSupported
  - Contract: no guarantee for VM-dependent behavior.

## 3. Data and Type Contract

- NULL vs empty string distinction: MustCompatible.
- Numeric and floating conversion consistency: MustCompatible.
- Column name and count retrieval for result sets: MustCompatible.
- BLOB columns in result sets: MustCompatible when native exposes blob length/read APIs and type id `8`; otherwise values may fall back to string/text path (DegradedCompatible).
- Advanced metadata parity beyond common usage: DegradedCompatible.

## 4. Error Contract

- App-facing exception hierarchy: MustCompatible.
  - Current: `SeekdbSqliteErrorMapper` maps return codes, optional `seekdb_sqlstate()`, and message heuristics (e.g. `UNIQUE`, `FOREIGN KEY`, SQLSTATE `23*` → `SQLiteConstraintException`). Generic query failures default to `SQLiteException` (not blanket constraint).
- Error messages with operation context: MustCompatible.
- SQLite-native error code parity: DegradedCompatible.

## 5. Cancellation and Interrupt

- `CancellationSignal` on `query(SupportSQLiteQuery, CancellationSignal)`: DegradedCompatible
  - Contract: `throwIfCanceled` before execution; JNI result fetch polls `isCanceled()` every 32 rows and throws `OperationCanceledException`. Native statement cancel/interrupt hooks depend on future SeekDB APIs.

## 6. Resource and Lifecycle Contract

- Cursor and statement close behavior: MustCompatible.
- Connection leak prevention guards: MustCompatible.
- Finalizer-only cleanup fallback: DegradedCompatible (best effort only).

## 7. Verification Requirements

For every MustCompatible item:

- At least one instrumentation regression test (when `libseekdb.so` is available).
- At least one failure-path or JVM contract test where native is optional.
- Explicit traceability to implementation unit and owner.

For every DegradedCompatible item:

- One documented behavior note (this matrix).
- One compatibility test proving deterministic fallback where feasible without native.
