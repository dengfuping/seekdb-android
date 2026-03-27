# Room P0 Scope and Acceptance

## 1. P0 Objective

Deliver the minimum complete compatibility set required to run Room in production-like workflows on seekdb-android.

## 2. P0 Critical Flows

- Database creation and open helper lifecycle.
- Schema migration path.
- Insert/update/delete via compiled statements.
- Query execution and entity mapping via cursor reads.
- Transaction wrapping for write operations.
- Error propagation for invalid SQL and constraint-like failures.

## 3. P0 Compatibility Requirements

- SupportSQLite-style open helper entry path must be functional.
- Statement bind types used by Room-generated code must be supported.
- Cursor must provide stable column index and typed access semantics.
- Begin/markSuccessful/end transaction sequence must be equivalent.
- Resource cleanup must be deterministic under normal and exceptional paths.

## 4. P0 Exclusions

- Full SQLite pragma parity.
- SQLite extension and VM-specific behaviors.
- Non-core metadata APIs not used by Room default codegen path.

## 5. P0 Test Plan

- Instrumentation tests:
  - create/open/migrate schema.
  - DAO insert and query roundtrip.
  - transaction commit and rollback.
  - nullability and default value handling.
  - conflict algorithm (`IGNORE`) behavior.
- Compatibility tests:
  - bind index conversion correctness.
  - cursor lifecycle and close safety.
  - repeated open/close reliability.

## 6. Done Definition

P0 is done when all conditions are true:

- All P0 critical flows pass on target ABIs in CI.
- No known native handle leak in P0 test suite.
- MustCompatible entries linked to Room P0 are fully passing.
- Documented gaps are limited to declared exclusions only.
