# Room / SupportSQLite compatibility on SeekDB (seekdb-android)

This document lists **concrete adaptation points** in seekdb-android so AndroidX `SupportSQLite` and Room run on the embedded SeekDB (MySQL/OceanBase–shaped) engine. For API-level contracts and test expectations, see [`compat-contract-matrix.md`](compat-contract-matrix.md) and [`room-p0-scope.md`](room-p0-scope.md).

## 1. Integration entry

| What | Where |
|------|--------|
| `Room.databaseBuilder(...).openHelperFactory(...)` | [`SeekdbCompat.factory()`](../../seekdb-android/src/main/java/com/oceanbase/seekdb/android/compat/SeekdbCompat.java) or [`SeekdbSQLite.supportOpenHelperFactory()`](../../seekdb-android/src/main/java/com/oceanbase/seekdb/android/sqlite/SeekdbSQLite.java) (same instance) |
| Optional process-wide native teardown | [`SeekdbCompat.shutdownEmbeddedEngine()`](../../seekdb-android/src/main/java/com/oceanbase/seekdb/android/compat/SeekdbCompat.java) — not tied to `SupportSQLiteOpenHelper.close()` so reopen and per-path `seekdb_open` behavior stay predictable |

## 2. SQL normalization (`SeekdbCompatSql`)

Room and Android SQLite emit **SQLite dialect**; the embed parser expects **MySQL/OceanBase** shapes. All statements that go through compat (`compileStatement`, `execSQL` paths that normalize) pass through [`SeekdbCompatSql.normalize`](../../seekdb-android/src/main/java/com/oceanbase/seekdb/android/compat/SeekdbCompatSql.java).

| Room / SQLite shape | Adaptation |
|---------------------|------------|
| `INSERT OR REPLACE` / `IGNORE` / `ABORT` / `FAIL` / `ROLLBACK` | Mapped to `REPLACE INTO`, `INSERT IGNORE INTO`, or plain `INSERT INTO` as appropriate |
| `UPDATE OR …` | Mapped to `UPDATE IGNORE` or plain `UPDATE` |
| `CREATE TEMP TABLE` (generic) | `TEMP` dropped — embed has no SQLite temp tables; tables become ordinary |
| `room_table_modification_log` | `CREATE TEMP TABLE …` → `CREATE TABLE IF NOT EXISTS …` so reopen does not hit “table already exists” |
| `CREATE TEMP TRIGGER` | `TEMP` dropped (`CREATE TRIGGER`) |
| Trigger bodies omitting `FOR EACH ROW` | Inserted before `BEGIN` (MySQL/OceanBase requirement) |
| `AUTOINCREMENT` | Rewritten to `AUTO_INCREMENT` |
| `INTEGER` in `CREATE TABLE` | Rewritten to `BIGINT` so Room `long` columns match 64-bit affinity |
| `nullif(?, 0)` in generated `INSERT` | Simplified to `?` (bind layer handles auto-increment key semantics) |

## 3. Statement layer (`SeekdbCompatStatement`)

| Topic | Behavior |
|-------|----------|
| Parameter indices | Android / Room use **1-based** binding; JNI uses **0-based** — converted in compat |
| Room auto-increment insert | If SQL matches Room’s leading `` `id` `` column pattern, binding **long `0`** for parameter **1** is sent as **SQL NULL** so the engine assigns the next id |
| Transient errors | `stmt_execute` may surface `EAGAIN`-class return codes; compat retries up to a bounded limit before surfacing |
| Typed reads | Numeric cells may arrive as strings (e.g. `"5.0"`); paths like `simpleQueryForLong` normalize so Room `COUNT(*)`-style queries behave |
| Failure diagnostics | Optional `seekdbAndroidDiag …` suffix on `SQLiteException` messages ([`SeekdbCompatDiagnostics`](../../seekdb-android/src/main/java/com/oceanbase/seekdb/android/compat/SeekdbCompatDiagnostics.java)) — **not** Logcat; only what callers see on thrown exceptions |

## 4. Database facade (`SeekdbCompatDatabase`)

| Topic | Behavior |
|-------|----------|
| Nested transactions | **Reference-counted depth**: inner `begin`/`end` pairs do not commit/rollback the engine until depth returns to zero — supports Room **InvalidationTracker** starting/finishing work around app transactions |
| `PRAGMA` **queries** Room needs | Routed to information_schema–backed cursors where required, e.g. `PRAGMA table_info(…)` → `information_schema.COLUMNS`; `PRAGMA foreign_key_list` → empty schema-shaped cursor; `INDEX_LIST` / `INDEX_XINFO` → empty cursors |
| `PRAGMA` **exec** at open | A set of no-op/ignored pragmas (`TEMP_STORE`, `PAGE_SIZE`, `SYNCHRONOUS`, `JOURNAL_MODE`, `CACHE_SIZE`, `FOREIGN_KEYS`, `RECURSIVE_TRIGGERS`) — parser cannot execute them as SQLite |
| `setForeignKeyConstraintsEnabled` | Still issues `PRAGMA foreign_keys = …` when a connection exists (compat contract) |
| `sqlite_master` probes | Room’s `RoomOpenHelper` queries are mapped to `information_schema.tables` equivalents (presence of `room_master_table`, non-`android_metadata` table count) |
| Default query materialization | Matrix-backed cursors by default; optional streaming windowed cursor via [`SeekdbSQLite.setStreamingQueryCursorsEnabled`](../../seekdb-android/src/main/java/com/oceanbase/seekdb/android/sqlite/SeekdbSQLite.java) (default **off** for Room stability) |

## 5. Open helper (`SeekdbOpenHelper`)

| Topic | Behavior |
|-------|----------|
| Init barrier | [`awaitReadyForExternalUse`](../../seekdb-android/src/main/java/com/oceanbase/seekdb/android/compat/SeekdbOpenHelper.java) blocks until `onCreate` / `onUpgrade` / `onConfigure` / `onOpen` complete so background Room work does not race schema setup |
| Same-thread reentrancy | `getWritableDatabase()` from `onConfigure` on the init thread does not wait (avoids self-deadlock) |

## 6. Invalidation (LiveData / `InvalidationTracker`)

Room installs **`CREATE TEMP TRIGGER`** DDL and writes to **`room_table_modification_log`** like on SQLite. After compat rewrites, triggers and the log table are **ordinary** objects on the embed engine — same overall model as [sqlite-android](https://github.com/requery/sqlite-android): invalidation is driven by **engine-executed triggers**, not a separate Java bridge.

## 7. Errors

[`SeekdbSqliteErrorMapper`](../../seekdb-android/src/main/java/com/oceanbase/seekdb/android/compat/SeekdbSqliteErrorMapper.java) maps native return codes (and optional SQLSTATE) to `SQLiteException` / `SQLiteConstraintException` etc., aligned with [`compat-contract-matrix.md`](compat-contract-matrix.md) §4.

## 8. Related docs

| Document | Focus |
|----------|--------|
| [`api-compat-layer.md`](api-compat-layer.md) | Layer goals and internal mapping rules |
| [`compat-contract-matrix.md`](compat-contract-matrix.md) | Must / degraded / not supported contracts |
| [`sqlite-android-parity-matrix.md`](sqlite-android-parity-matrix.md) | Java surface vs sqlite-android |
| [`seekdb-engine-android.md`](seekdb-engine-android.md) | Engine repo embed work (`libseekdb.so`, process notes) |
