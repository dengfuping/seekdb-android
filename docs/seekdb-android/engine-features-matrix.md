# SeekDB engine features vs SQLite (pragma / extensions / hooks)

This matrix tracks **engine capability** for the full SQLite replacement track. Java API may expose `UnsupportedOperationException` until the engine and JNI wire a feature.

| Feature | Target (sqlite-android / SQLite) | SeekDB / seekdb-android status |
|---------|-----------------------------------|--------------------------------|
| `PRAGMA foreign_keys` | Common | Degraded: issued from compat when connection exists |
| `PRAGMA journal_mode` / WAL | Common | Degraded: flags only in compat; engine may differ |
| `ATTACH DATABASE` | Common | N/A / Planned (Phase 7) |
| JSON1 extension | sqlite-android advertises | N/A until SeekDB exposes equivalent |
| FTS5 | sqlite-android advertises | N/A until SeekDB exposes equivalent |
| User-defined SQL functions | `SQLiteFunction` / custom | N/A / Planned + JNI callback design |
| Update / commit / authorizer hooks | sqlite-android / AOSP | N/A / Planned + engine support |
| `sqlite3_interrupt` style cancel | Long queries | Degraded: `CancellationSignal` + row-loop poll; stmt-level cancel TBD |

Update this table when `libseekdb.so` or JNI adds a capability; link to [abi-parity-checklist.md](abi-parity-checklist.md).
