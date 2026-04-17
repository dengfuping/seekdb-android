# sqlite-android parity matrix (Java surface)

Room-specific adaptation details (SeekDB embed rewrites, catalog routing): [`room-sqlite-compat.md`](room-sqlite-compat.md).

Reference: [requery/sqlite-android](https://github.com/requery/sqlite-android) (`io.requery.android.database.*`).

Legend: **Pass** (behaviour covered or delegated), **Degraded** (subset / different semantics), **N/A** (not applicable or engine limitation), **Planned** (on roadmap).

## Factory / SupportSQLite

| Item | Status | Notes |
|------|--------|--------|
| `RequerySQLiteOpenHelperFactory` | Degraded | Use [SeekdbOpenHelperFactory](../../seekdb-android/src/main/java/com/oceanbase/seekdb/android/compat/SeekdbOpenHelperFactory.java) (`SeekdbCompat.factory()`) |
| Room `openHelperFactory` | Pass | Same integration pattern as sqlite-android README |

## High-value sqlite-android classes (target parity)

| Class | Status | Notes |
|-------|--------|--------|
| `SQLiteDatabase` | Planned | Phase 6: public facade or attributed fork |
| `SQLiteOpenHelper` | Degraded | [SeekdbSQLite](../../seekdb-android/src/main/java/com/oceanbase/seekdb/android/sqlite/SeekdbSQLite.java) + [SeekdbFrameworkOpenHelper](../../seekdb-android/src/main/java/com/oceanbase/seekdb/android/sqlite/framework/SeekdbFrameworkOpenHelper.java) (`SupportSQLiteOpenHelper`); full `io.requery` `SQLiteOpenHelper` mirror TBD |
| `SQLiteConnection` / `SQLiteConnectionPool` / `SQLiteSession` | Degraded | [SeekdbConnectionPool](../../seekdb-android/src/main/java/com/oceanbase/seekdb/android/runtime/SeekdbConnectionPool.java) + [SeekdbSerializedSession](../../seekdb-android/src/main/java/com/oceanbase/seekdb/android/runtime/SeekdbSerializedSession.java) + `SeekdbRuntime.installGlobalPrimary`; multi-connection ABI TBD |
| `CursorWindow` / `AbstractWindowedCursor` | Degraded | [SeekdbCursorWindowUtil](../../seekdb-android/src/main/java/com/oceanbase/seekdb/android/database/SeekdbCursorWindowUtil.java) + `fillChunk`; query path [SeekdbWindowedCursor](../../seekdb-android/src/main/java/com/oceanbase/seekdb/android/database/SeekdbWindowedCursor.java) when streaming policy is on (buffer + window; not native scroll) |
| `SQLiteCursor` / `SQLiteDirectCursorDriver` | Degraded | [SeekdbDefaultCursorDriver](../../seekdb-android/src/main/java/com/oceanbase/seekdb/android/database/driver/SeekdbDefaultCursorDriver.java) + [SeekdbSQLiteCursorDriver](../../seekdb-android/src/main/java/com/oceanbase/seekdb/android/database/driver/SeekdbSQLiteCursorDriver.java); `io.requery` class names not shipped |
| `SQLiteStatement` / `SQLiteProgram` | Degraded | [SeekdbCompatStatement](../../seekdb-android/src/main/java/com/oceanbase/seekdb/android/compat/SeekdbCompatStatement.java) via SupportSQLite |
| Custom functions / extensions | N/A / Planned | Phase 7 + engine |

## Runtime utilities

| Item | Status | Notes |
|------|--------|--------|
| Database Inspector (`androidx.sqlite.inspection`) | Degraded | [seekdb-android-inspection](../../seekdb-android-inspection/) (fork of sqlite-android-inspection for `SeekdbCompatDatabase`) + [inspector-setup.md](inspector-setup.md); schema SQL still SQLite-oriented; cursor invalidation path reduced vs requery |
| Streaming row iteration (no full `Object[][]`) | Pass | [SeekdbResultScanner](../../seekdb-android/src/main/java/com/oceanbase/seekdb/android/runtime/SeekdbResultScanner.java) + `nativeResultReadNextRowTyped`; optional query `Cursor` via [SeekdbWindowedCursor](../../seekdb-android/src/main/java/com/oceanbase/seekdb/android/database/SeekdbWindowedCursor.java) when [SeekdbStreamingPolicy](../../seekdb-android/src/main/java/com/oceanbase/seekdb/android/runtime/SeekdbStreamingPolicy.java) is enabled (default off for Room) |
| `CrossProcessCursorWrapper` | N/A | Document: use framework wrapper if exposing `Cursor` across processes |

## Engine features (pragma / extensions)

| Feature | Status | Notes |
|---------|--------|--------|
| `PRAGMA foreign_keys` | Degraded | Issued from compat when connection exists |
| `ATTACH DATABASE` | N/A / Planned | Phase 7 |
| JSON1 / FTS5 / UDF | N/A / Planned | SeekDB engine matrix |
