# seekdb-android

[![ci](https://github.com/oceanbase/seekdb-android/actions/workflows/ci.yml/badge.svg)](https://github.com/oceanbase/seekdb-android/actions/workflows/ci.yml)

Android library: **SeekDB** via JNI, with a **Room / `SupportSQLite`** compatibility surface (and a roadmap toward broader SQLite-shaped APIs).

## Documentation

Design, API layers, testing, and release notes live under [`docs/seekdb-android/`](docs/seekdb-android/README.md).

## Quick start (Gradle)

**Maven coordinates** (when published):

```gradle
implementation "com.oceanbase.seekdb:seekdb-android:${version}"
```

Local verification:

```bash
./gradlew :seekdb-android:assembleDebug
./gradlew :seekdb-android:publishToMavenLocal
```

Room:

```java
import com.oceanbase.seekdb.android.sqlite.SeekdbSQLite;

Room.databaseBuilder(context, AppDb.class, "app.db")
    .openHelperFactory(SeekdbSQLite.supportOpenHelperFactory())
    .build();
```

## JitPack (optional)

Add repository **`https://jitpack.io`** and depend on **`com.github.oceanbase:seekdb-android:<tag>`** (GitHub **`oceanbase/seekdb-android`**). This repo includes `jitpack.yml` (JDK 17). Confirm the resolved artifact after the first JitPack build.

## Maven Central

Releases use **`com.vanniktech.maven.publish`**. On **`master` branch push**, CI runs **`./gradlew publish`** when `SonatypeUsername` / `SonatypePassword` and signing keys are configured (see `.github/workflows/ci.yml`).

## License

See [`LICENSE`](LICENSE) and [`seekdb-android/NOTICE`](seekdb-android/NOTICE).
