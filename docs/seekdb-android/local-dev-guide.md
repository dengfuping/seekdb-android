# Local Development Guide

## Prerequisites

- JDK 17
- Android SDK and platform tools
- Use project `./gradlew` (Gradle wrapper, pinned to 9.3.1)
- Optional for full integration: `libseekdb.so` available to runtime loader

## Build

```bash
./gradlew :seekdb-android:assembleDebug
```

## Unit Tests

```bash
./gradlew :seekdb-android:testDebugUnitTest
```

## Instrumentation Tests

Start an emulator or connect a device, then run:

```bash
./gradlew :seekdb-android:connectedDebugAndroidTest
```

## Notes on Native Availability

- If `libseekdb.so` is missing, native-dependent tests are skipped via assumptions.
- For meaningful Room/compat integration validation, ensure native library is available.

## Recommended Iteration Loop

- Modify JNI or compat code
- Run unit tests
- Run instrumentation tests for compat + Room scenarios
- Update `implementation-status.md` with behavior changes
