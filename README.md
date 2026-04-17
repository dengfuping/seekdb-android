# seekdb-android

[![ci](https://github.com/oceanbase/seekdb-android/actions/workflows/ci.yml/badge.svg)](https://github.com/oceanbase/seekdb-android/actions/workflows/ci.yml)

Android library: **SeekDB** over JNI, with a **Room / `SupportSQLite`** surface similar to typical SQLite-on-Android usage (broader `SQLiteDatabase`-shaped APIs are on the roadmap).

## Room integration

Use **`SeekdbCompat.factory()`** as the `SupportSQLiteOpenHelper.Factory` when building your `RoomDatabase`. Room’s `@Entity` / `@Dao` / `@Database` stay the same; only the open-helper factory and native packaging change.

### 1. Gradle

Published coordinates (pin a version from release notes or [`docs/seekdb-android/testing-and-release.md`](docs/seekdb-android/testing-and-release.md)):

```gradle
implementation "com.oceanbase.seekdb:seekdb-android:${version}"
```

Local checkout:

```bash
./gradlew :seekdb-android:assembleDebug
./gradlew :seekdb-android:publishToMavenLocal
```

### 2. Ship `libseekdb.so`

Package **`libseekdb.so`** per ABI (e.g. under `app/src/main/jniLibs/<abi>/`). Without it, `SeekdbClient.isNativeAvailable()` is false and the compat layer cannot open the engine.

At runtime you can probe:

```java
import com.oceanbase.seekdb.android.nativeapi.SeekdbClient;

if (!SeekdbClient.isNativeAvailable()) {
    // Show error or fall back; do not build Room with SeekdbCompat in this state.
}
```

(`com.oceanbase.seekdb.android.sqlite.SeekdbSQLite` also exposes `isNativeLibraryAvailable()` if you use that entry point.)

### 3. Wire Room

```java
import androidx.room.Room;
import com.oceanbase.seekdb.android.compat.SeekdbCompat;

AppDatabase db = Room.databaseBuilder(context, AppDatabase.class, "app.db")
        .openHelperFactory(SeekdbCompat.factory())
        .build();
```

**Database name / path**

| `Room.databaseBuilder(..., name)` | Effect |
|-----------------------------------|--------|
| **Relative** name (e.g. `"app.db"`) | Resolved with `Context.getDatabasePath` — data under app-private internal storage. |
| **Absolute** path (e.g. `new File(context.getFilesDir(), "seekdb/app.db").getAbsolutePath()`) | SeekDB uses **the parent directory of that file** as the engine root (`store/`, `log/`, etc. sit next to the `.db`). |

Prefer **internal** paths (`getFilesDir()`, `getDatabasePath`) first when validating; some devices or engine builds are sensitive to **app-specific external** roots (`getExternalFilesDir`). See [`docs/seekdb-android/room-sqlite-compat.md`](docs/seekdb-android/room-sqlite-compat.md) for dialect / `PRAGMA` notes.

### 4. Optional APIs

- **Streaming large cursors** (off by default): `com.oceanbase.seekdb.android.sqlite.SeekdbSQLite.setStreamingQueryCursorsEnabled(true)` — only if you use the `SeekdbSQLite` helpers.
- **Full native teardown** when no seekdb-backed DB is needed anymore: `SeekdbCompat.shutdownEmbeddedEngine()` (not the same as closing a single `SupportSQLiteOpenHelper`; see Javadoc).

### 5. Database Inspector (debug)

Add **`debugImplementation`** of `seekdb-android-inspection` and follow [`docs/seekdb-android/inspector-setup.md`](docs/seekdb-android/inspector-setup.md) (includes AndroidX inspection snapshot repo).

---

## Documentation

Design notes, testing, release: [`docs/seekdb-android/`](docs/seekdb-android/README.md).

## Alternative factory (`SeekdbSQLite`)

If you standardize on the `SeekdbSQLite` entry point:

```java
import com.oceanbase.seekdb.android.sqlite.SeekdbSQLite;

Room.databaseBuilder(context, AppDb.class, "app.db")
        .openHelperFactory(SeekdbSQLite.supportOpenHelperFactory())
        .build();
```

Do **not** mix sqlite-android and seekdb-android on the same classpath expecting two SQLite backends.

## JitPack (optional)

Repository **`https://jitpack.io`**, dependency **`com.github.oceanbase:seekdb-android:<tag>`** (repo **`oceanbase/seekdb-android`**). This repo includes `jitpack.yml` (JDK 17). Verify the resolved artifact after the first JitPack build.

## Maven Central

Releases use **`com.vanniktech.maven.publish`**. On **`master` branch push**, CI runs **`./gradlew publish`** when `SonatypeUsername` / `SonatypePassword` and signing keys are configured (see `.github/workflows/ci.yml`).

---

## Migration (concise)

### From stock Room (framework SQLite)

1. Add **seekdb-android** + ship **`libseekdb.so`** for your ABIs.  
2. Add **`.openHelperFactory(SeekdbCompat.factory())`** (or `SeekdbSQLite.supportOpenHelperFactory()`) to `Room.databaseBuilder(...)`.  
3. Run your usual schema / CRUD / invalidation tests; fix SQL only where the engine dialect differs from SQLite ([compat matrix](docs/seekdb-android/compat-contract-matrix.md), [Room notes](docs/seekdb-android/room-sqlite-compat.md)).

Direct `android.database.sqlite.SQLiteDatabase` usage outside Room is unchanged only if you keep using framework SQLite for those paths; long-term, prefer `SupportSQLite` / Room or future seekdb-shaped APIs.

### From Room + sqlite-android

1. **Remove** the `io.requery:sqlite-android` dependency (and orphans).  
2. **Add** seekdb-android + **`libseekdb.so`**; attach **`SeekdbCompat.factory()`** (or `SeekdbSQLite.supportOpenHelperFactory()`) to `Room.databaseBuilder`.  
3. Replace any **`io.requery.android.database.*`** imports with this library’s APIs or access DB only through Room / `SupportSQLite`.  
4. **Inspector:** add **`seekdb-android-inspection`** per [inspector-setup.md](docs/seekdb-android/inspector-setup.md).

Finer behavior tiers: [compat contract matrix](docs/seekdb-android/compat-contract-matrix.md).

## License

See [`LICENSE`](LICENSE) and [`seekdb-android/NOTICE`](seekdb-android/NOTICE).
