# SeekDB C ABI parity checklist (seekdb-android ↔ full SQLite replacement)

This document is the working contract between **SeekDB engine** (`libseekdb.so`) and **seekdb-android** JNI, aligned with the official C API in **seekdb** (`seekdb.h`). Status values:

| Status | Meaning |
|--------|---------|
| **wired** | Loaded via `dlsym` in [seekdb_android_jni.cpp](../../seekdb-android/src/main/cpp/seekdb_android_jni.cpp); required for `nativeIsAvailable() == true` when strict set is complete |
| **optional** | `dlsym` best-effort; absence enables degraded Java behaviour |
| **planned** | Needed for sqlite-android-class parity; not yet in JNI |
| **n/a** | Engine will not implement; document in [sqlite-android-parity-matrix.md](sqlite-android-parity-matrix.md) |

`SeekdbNativeBridge.nativeAbiVersion()` is **1** for the first published JNI/engine contract (see implementation in JNI). Bump it when **wired** or **optional** surface changes in a breaking way.

## Core lifecycle

| Symbol / capability | Status | Notes |
|---------------------|--------|--------|
| `seekdb_open` | wired | Embedded mode: `port <= 0` in `nativeOpen` |
| `seekdb_open_with_service` | wired | Server mode: `port > 0` in `nativeOpen` |
| `seekdb_close` | wired | |
| `seekdb_connect` | wired | |
| `seekdb_connect_close` | wired | JNI `nativeDisconnect` |
| `seekdb_begin` / `seekdb_commit` / `seekdb_rollback` | wired | |
| `seekdb_last_error_code` / `seekdb_last_error` | wired | Thread-local last error |
| `seekdb_errno` | optional | |
| `seekdb_sqlstate` | optional | JNI: `nativeLastSqlState(long connectionPtr)`; no-arg returns empty |

## Query / result

| Symbol / capability | Status | Notes |
|---------------------|--------|--------|
| `seekdb_query` | wired | JNI `nativeQuery`; not `seekdb_query_cstr` |
| `seekdb_result_free` | wired | |
| `seekdb_num_fields` | wired | JNI `nativeResultColumnCount` |
| `seekdb_fetch_field_direct` | wired | Column MySQL `type` for typed boxing; JNI `nativeResultColumnTypeId` returns raw `SeekdbField.type` |
| `seekdb_result_column_name` / `seekdb_result_column_name_len` | wired | |
| `seekdb_fetch_row` | wired | Replaces legacy `seekdb_result_row_next`; EOF when `NULL` and no thread-local error |
| `seekdb_row_*` getters | wired | string / int64 / double / bool / null |

## Prepared statements

| Symbol / capability | Status | Notes |
|---------------------|--------|--------|
| `seekdb_stmt_init` | wired | Before `seekdb_stmt_prepare` |
| `seekdb_stmt_prepare` | wired | |
| `seekdb_stmt_bind_param` + `SeekdbBind` | wired | JNI accumulates binds per parameter index |
| `seekdb_stmt_execute` | wired | |
| `seekdb_stmt_store_result` | wired | Resets row cursor before Java reads via `fetch_row` |
| `seekdb_stmt_result_metadata` | wired | Result handle for column metadata + row fetch |
| `seekdb_stmt_close` | wired | |
| `seekdb_stmt_param_count` | wired | Sizes JNI bind array |
| `seekdb_stmt_affected_rows` / `seekdb_stmt_insert_id` | wired | |
| `seekdb_stmt_reset` | optional | JNI: `nativeStmtReset`; -1 if symbol missing |
| JNI `nativeStmtClearBindings` | wired | No C symbol; JNI resets `StmtJniState` buffers |
| `nativeResultReadNextRowTyped` | wired | EOF = empty `Object[]` |

## Pooling / concurrency

| Capability | Status | Notes |
|------------|--------|--------|
| Second connection mode (read-only) | planned | Model `SQLiteConnectionPool` primary vs reader |
| Thread-safe shared connection | n/a | Single connection + app serialization unless engine adds mutex |

## JNI bridge inventory

See also: [SeekdbNativeBridge.java](../../seekdb-android/src/main/java/com/oceanbase/seekdb/android/core/SeekdbNativeBridge.java).

---

## seekdb-android 对 SeekDB C ABI 的需求说明（引擎对接）

### 1. 需求分层

| 层级 | 目标 | 引擎侧交付物 |
|------|------|----------------|
| **P0 — 严格集** | `nativeIsAvailable() == true`，Room/SupportSQLite 主路径可跑 | 上表 **wired** 所列 `seekdb.h` 符号全部导出且行为稳定；`seekdb_fetch_row` + `seekdb_row_*`；`seekdb_stmt_*` + `SeekdbBind` 完成 prepare/bind/execute、`seekdb_stmt_result_metadata` / `seekdb_stmt_store_result`、受影响行与 last insert id |
| **P1 — 可选增强** | 错误映射、语句复用 | **optional**：`seekdb_sqlstate(handle)`、`seekdb_stmt_reset` |
| **P2 — 全替换 / sqlite-android 级** | 连接池、可中断、减少 Java 侧缓冲、调试对齐 | **planned**：见下文；另见 [engine-features-matrix.md](engine-features-matrix.md) |

### 2. P2 能力清单（当前 JNI 未接或仅 Java 降级）

| 需求 | 动机 | 建议 C 面方向（引擎可改名，需约定语义） |
|------|------|------------------------------------------|
| **语句/连接级取消** | 替代仅 JNI 每 N 行轮询 `CancellationSignal`；长查询可尽快结束 | 如：`seekdb_stmt_interrupt` / `seekdb_connection_interrupt` |
| **`seekdb_stmt_bind_parameter_count` 或已有 `param_count` 暴露** | 与 SQLite 一致校验绑定、日志与测试 | 已有 `seekdb_stmt_param_count` |
| **第二连接 / 只读连接** | 对齐 `SQLiteConnectionPool` 多读单写 | 独立连接创建 API；语义与事务需定义 |

### 3. 语义契约

- **错误与 EOF**：`seekdb_fetch_row` 返回 `NULL` 时，若 `seekdb_last_error_code() == 0` 视为 EOF；非 0 视为错误（与 `seekdb.h` 注释一致）。
- **列类型**：`SeekdbField.type` 为 MySQL field type 枚举；JNI 映射为 Long / Double / Boolean / byte[] / String。
- **所有权**：`seekdb_result_free` 释放查询/语句元数据结果集；`seekdb_stmt_close` 释放语句并 JNI 侧移除 `StmtJniState`。

### 4. ABI 版本

- Java：`SeekdbNativeBridge.nativeAbiVersion()` 当前为 **1**（首版对齐 `seekdb.h`）。
- **breaking change** 包括：函数签名变更、句柄类型变更、rc 语义变更、wired 符号改为 optional 或删除。

### 5. 相关文档

- SQL 语言与扩展能力：[engine-features-matrix.md](engine-features-matrix.md)
- Java 行为与降级：[sqlite-android-parity-matrix.md](sqlite-android-parity-matrix.md)、[compat-contract-matrix.md](compat-contract-matrix.md)
