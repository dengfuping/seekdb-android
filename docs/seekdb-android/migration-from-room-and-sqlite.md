# Migrating from Room / SQLite to seekdb-android

The full user-facing migration guide (package names, steps, optional settings, and validation tips) lives in the repository root [**README.md**](../../README.md#migration-guide).

Use this page as a short index:

| Topic                                        | Document                                                             |
| -------------------------------------------- | -------------------------------------------------------------------- |
| Room SQL / `PRAGMA` / transactions on SeekDB | [`room-sqlite-compat.md`](room-sqlite-compat.md)                     |
| Compatibility levels (must / degraded)       | [`compat-contract-matrix.md`](compat-contract-matrix.md)             |
| Java surface vs sqlite-android               | [`sqlite-android-parity-matrix.md`](sqlite-android-parity-matrix.md) |
| Dependencies, testing, release               | [`testing-and-release.md`](testing-and-release.md)                   |
| Embedded engine and `libseekdb.so`           | [`seekdb-engine-android.md`](seekdb-engine-android.md)               |
