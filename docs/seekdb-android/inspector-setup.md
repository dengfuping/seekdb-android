# Database Inspector (seekdb-android)

This project vendors the [sqlite-android-inspection](https://github.com/requery/sqlite-android/tree/master/sqlite-android-inspection) sources under `seekdb-android-inspection`, adapted for **`SeekdbCompatDatabase`** / **`SeekdbCompatStatement`** instead of `io.requery.android.database.sqlite.*`.

## Repository setup (Gradle)

`androidx.inspection:inspection` is **not** on Maven Central. The root **`settings.gradle`** already adds the AndroidX snapshot repository used by sqlite-android. Optional overrides in **`gradle.properties`**:

```properties
androidx.inspection.version=1.0.0-SNAPSHOT
androidx.inspection.snapshot.buildId=15127136
```

## App dependency (debug only)

In the **app** module:

```gradle
debugImplementation project(':seekdb-android-inspection')
```

Use the same snapshot repository in **your** app if you consume `seekdb-android-inspection` from Maven (not `project()`) so `androidx.inspection:inspection` resolves:

```gradle
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://androidx.dev/snapshots/builds/15127136/artifacts/repository")
            content { includeGroup("androidx.inspection") }
        }
    }
}
```

Release builds should **not** include the inspection AAR; keep it `debugImplementation` only.

## Integration

Open the database with **`SeekdbCompat.factory()`** / **`SeekdbSQLite.supportOpenHelperFactory()`** as usual. The inspector discovers **`SeekdbCompatDatabase`** instances via `ArtTooling.findInstances` (no static `SQLiteDatabase.openDatabase` hook on SeekDB).

**Paths:** `SeekdbCompatDatabase#getPath()` is set to the **absolute** `Context#getDatabasePath(name)` so the UI matches the real Room DB file. The inspector also scans `Application#databaseList()`; SeekDB may create sibling files (`etc`, `log`, `run`, `store`) under the app databases directory — those are **filtered out** so they do not appear as ghost “(closed)” databases.

**If the database list is empty:** discovery uses JVMTI `findInstances` plus **exit hooks** on `SeekdbOpenHelper#getWritableDatabase` / `getReadableDatabase`. After connecting App Inspection, **open a screen that touches Room** (or restart the process with the inspector already attached) so at least one of those methods runs while tracking is enabled.

## Limitations vs sqlite-android

- **Schema query** (`sQueryTableInfo` in `SqliteInspector`) still assumes SQLite `sqlite_master` / `pragma_*` shapes; on SeekDB / MySQL semantics, **schema panels may be empty or partial** until mapped to `information_schema` (future work).
- **Cursor-close invalidation** path (requery `rawQueryWithFactory`) is **not** hooked; statement + transaction hooks remain.
- **Lock / export** paths use `SimpleSQLiteQuery("BEGIN IMMEDIATE;")` / `ROLLBACK` — engine support may vary.

## Manual verification

1. Install a **debug** build on a device or emulator with Studio Database Inspector support.
2. **App Inspection → Database Inspector**, pick the process and database.
3. If nothing appears, check Logcat for inspection errors and confirm `libseekdb.so` loads.

See also [`VENDOR.txt`](../../seekdb-android-inspection/VENDOR.txt) in the inspection module.
