# Testing and Release Plan

## 1. Test Pyramid

- Unit tests:
  - API argument validation.
  - index conversion and type mapping.
  - error mapping logic.
  - streaming policy default (`SeekdbStreamingPolicy` / `SeekdbSQLite` facade).
- Integration tests:
  - JNI wrapper lifecycle and resource management.
  - statement and result end-to-end flows.
- Instrumentation tests:
  - Room P0 functional scenarios.
  - compatibility matrix regressions.

## 2. CI Gates

- Build gate:
  - Android library compiles with JNI artifacts for target ABIs.
- Quality gate:
  - MustCompatible test suite must pass.
  - Room P0 suite must pass.
- Stability gate:
  - no known deterministic leak in automated stress run.

Current workflow (**aligned with [sqlite-android `ci.yml`](https://github.com/requery/sqlite-android/blob/master/.github/workflows/ci.yml)**):

- GitHub Actions file: `.github/workflows/ci.yml` (workflow name `ci`)
- `permissions: contents: read`
- Triggers: **`on: [push, pull_request]`** (all branches)
- Runner: **`macos-latest`**
- Matrix: **`api-level: [29]`**, emulator **`arch: x86`**, same `emulator-options` / `disable-animations` as upstream
- Steps: checkout (`fetch-depth: 1`) → JDK 17 (**`distribution: adopt`**) → **`./gradlew connectedAndroidTest --stacktrace`** (root task; publishes `:seekdb-android` instrumentation) → upload `**/build/reports/androidTests/connected/**` → **`./gradlew publish`** when **`refs/heads/master` + `push`** with `SonatypeUsername` / `SonatypePassword` → `ORG_GRADLE_PROJECT_mavenCentral*`

**Note:** Snapshot publish matches upstream (**`master` only**). If the default branch is **`main`**, either rename default branch to **`master`** or add a separate release process; the workflow intentionally does not publish on `main` to stay identical to sqlite-android.

Local / extra gates (not in upstream CI): run `./gradlew :seekdb-android:assembleDebug` and `./gradlew :seekdb-android:testDebugUnitTest` before push if desired.

## 3. Performance Baseline

- Statement execution latency baseline.
- Query cursor traversal baseline.
- Transaction throughput baseline.
- Performance smoke test:
  - `SeekdbCompatPerformanceSmokeTest` validates baseline insert+query budget.

Track delta between releases to avoid regressions.

## 4. Release Artifacts

- AAR package.
- Native libs for declared ABIs.
- API docs and compatibility matrix snapshot.
- Known limitations and migration notes.
- Maven publication via **`com.vanniktech.maven.publish`** (aligned with sqlite-android); local verify: `./gradlew :seekdb-android:publishToMavenLocal`. Central release and signing require `ORG_GRADLE_PROJECT_mavenCentralUsername` / `Password` and signing keys (see sqlite-android CI on `master`).

## 5. Versioning and Compatibility Notes

- Semantic versioning for library API.
- Compatibility note per release:
  - Room P0 status.
  - MustCompatible coverage status.
  - newly supported degraded/not-supported items if changed.

## 6. Release Checklist

- All P0 tests passing in CI.
- Compatibility matrix reviewed and updated.
- ABI package contents verified.
- Changelog prepared.

## 7. Local Execution Notes

Without Gradle wrapper in repository:

- Ensure local Gradle 8.13 is available in PATH.
- Run:
  - `gradle :seekdb-android:assembleDebug`
  - `gradle :seekdb-android:testDebugUnitTest`

For instrumentation tests:

- Use a connected Android device or emulator.
- Run:
  - `gradle :seekdb-android:connectedDebugAndroidTest`

Native requirement:

- `libseekdb.so` must be available for full integration tests.
- Tests with native availability assumptions will be skipped when native library is absent.

Stale device state:

- If instrumentation fails after engine or schema changes (e.g. migration **downgrade** errors), clear the test package: `adb shell pm clear com.oceanbase.seekdb.android.test`, reinstall if needed, then rerun. See **`docs/seekdb-android/seekdb-engine-android.md`** for the full note.

Engine alignment:

- Android-specific behavior in the **seekdb** repository (RS reporting, DDL logging, signal/CPU startup, stmt write path) is summarized in **`docs/seekdb-android/seekdb-engine-android.md`** for reviewers and release notes.
