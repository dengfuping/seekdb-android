# SeekDB C ABI parity checklist (seekdb-android ↔ full SQLite replacement)

This document is the working contract between **SeekDB engine** (`libseekdb.so`) and **seekdb-android** JNI. Status values:

| Status | Meaning |
|--------|---------|
| **wired** | Loaded via `dlsym` in [seekdb_android_jni.cpp](../../seekdb-android/src/main/cpp/seekdb_android_jni.cpp); required for `nativeIsAvailable() == true` when strict set is complete |
| **optional** | `dlsym` best-effort; absence enables degraded Java behaviour |
| **planned** | Needed for sqlite-android-class parity; not yet in JNI |
| **n/a** | Engine will not implement; document in [sqlite-android-parity-matrix.md](sqlite-android-parity-matrix.md) |

Bump **`SEEKDB_ABI_VERSION`** / `SeekdbNativeBridge.nativeAbiVersion()` when **wired** or **optional** surface changes in a breaking way.

## Core lifecycle

| Symbol / capability | Status | Notes |
|---------------------|--------|--------|
| `seekdb_open` / `seekdb_close` | wired | |
| `seekdb_connect` / `seekdb_disconnect` | wired | |
| `seekdb_begin` / `seekdb_commit` / `seekdb_rollback` | wired | |
| `seekdb_last_error_code` / `seekdb_last_error` | wired | |
| `seekdb_sqlstate` | optional | |

## Query / result

| Symbol / capability | Status | Notes |
|---------------------|--------|--------|
| `seekdb_query_cstr` | wired | |
| `seekdb_result_free` | wired | |
| `seekdb_result_column_count` | wired | |
| `seekdb_result_column_name` / `_len` | wired | |
| `seekdb_result_column_type_id` | wired | |
| `seekdb_result_row_next` | wired | Basis for **streaming** row reads |
| `seekdb_row_*` getters | wired / optional blob | Blob symbols optional |

## Prepared statements

| Symbol / capability | Status | Notes |
|---------------------|--------|--------|
| `seekdb_stmt_prepare` / `seekdb_stmt_close` | wired | |
| `seekdb_stmt_bind_value` + `seekdb_value_*` | wired | |
| `seekdb_stmt_execute` | wired | |
| `seekdb_stmt_affected_rows` / `seekdb_stmt_insert_id` | wired | |
| `seekdb_value_set_blob` | optional | Fallback binds blob bytes as string buffer |
| `seekdb_stmt_reset` | optional | JNI: {@code nativeStmtReset}; -1 if symbol missing |
| `seekdb_stmt_clear_bindings` | optional | JNI: {@code nativeStmtClearBindings}; -1 if symbol missing |
| `nativeResultReadNextRowTyped` | wired | JNI incremental row read; EOF = empty {@code Object[]} |
| `seekdb_stmt_bind_parameter_count` | planned | Debugging / parity with sqlite-android |
| `seekdb_stmt_step` (row-producing without full execute) | planned | If distinct from execute+result in your ABI |
| Statement / connection **cancel** flag | planned | Cooperative interrupt during long scans |

## Pooling / concurrency

| Capability | Status | Notes |
|------------|--------|--------|
| Second connection mode (read-only) | planned | Model `SQLiteConnectionPool` primary vs reader |
| Thread-safe shared connection | n/a | Single connection + app serialization unless engine adds mutex |

## JNI bridge inventory

See also: [SeekdbNativeBridge.java](../../seekdb-android/src/main/java/com/oceanbase/seekdb/android/core/SeekdbNativeBridge.java).

---

## seekdb-android 对 SeekDB C ABI 的需求说明（引擎对接）

本节把上表按**优先级**和**语义契约**收拢，便于引擎侧排期与验收。

### 1. 需求分层

| 层级 | 目标 | 引擎侧交付物 |
|------|------|----------------|
| **P0 — 严格集** | `nativeIsAvailable() == true`，Room/SupportSQLite 主路径可跑 | 上表 **wired** 所列符号全部导出且行为稳定；`seekdb_result_row_next` + `seekdb_row_*`（至少 string/int64/double/bool/null）可顺序扫完结果；`seekdb_stmt_*` 与 `seekdb_value_*` 完成 prepare/bind/execute/取受影响行与 last insert rowid |
| **P1 — 可选增强** | 错误映射、BLOB、语句复用更贴近 SQLite | **optional**：`seekdb_sqlstate`、`seekdb_value_set_blob`、`seekdb_row_get_blob_len` / `seekdb_row_get_blob`、`seekdb_stmt_reset`、`seekdb_stmt_clear_bindings` |
| **P2 — 全替换 / sqlite-android 级** | 连接池、可中断、减少 Java 侧缓冲、调试对齐 | **planned**：见下节「P2 能力清单」；另见 [engine-features-matrix.md](engine-features-matrix.md)（pragma/ATTACH/hook/扩展等**语言面**能力） |

### 2. P2 能力清单（当前 JNI 未接或仅 Java 降级）

| 需求 | 动机 | 建议 C 面方向（引擎可改名，需约定语义） |
|------|------|------------------------------------------|
| **语句/连接级取消** | 替代仅 JNI 每 N 行轮询 `CancellationSignal`；长查询可尽快结束 | 如：`seekdb_stmt_interrupt` / `seekdb_connection_interrupt`，或连接上的 cancel flag，与 `seekdb_stmt_execute` / `seekdb_result_row_next` 协同返回明确 rc |
| **可重复 step 与 reset 语义** | 对齐 `sqlite3_step` / `reset`；便于与 `AbstractWindowedCursor` 原生填窗 | 若与当前「execute 一次产出 `SeekdbResult`」模型等价，则文档化即可；若需分步产出行，需 **step** 与 **reset** 的明确 ABI（与 `seekdb_stmt_reset` 关系写清） |
| **`seekdb_stmt_bind_parameter_count`** | 与 SQLite 一致校验绑定、日志与测试 | 返回 `?` / `:name` 解析后的参数个数 |
| **结果行数或低成本统计** | Java `getCount()` 当前往往需扫到 EOF；大结果集成本高 | 若引擎能 O(1) 或单次协议返回总行数，可增加 `seekdb_result_row_count` 等（无则保持「扫完才知」并在文档标明） |
| **原生向 buffer 填窗（可选）** | 减少 JNI 每单元 `CallObjectMethod`，贴近 AOSP `CursorWindow` | 如：在 native 侧写入连续内存/结构，由 JNI 一次性拷入 `CursorWindow`（需与 Android `CursorWindow` 布局或中间格式约定） |
| **第二连接 / 只读连接** | 对齐 `SQLiteConnectionPool` 多读单写 | 连接创建 flag 或独立 `seekdb_connect_readonly`；语义与事务、WAL 快照需定义 |
| **线程安全** | 多线程访问同一 `SeekdbConnection` | 要么连接 API **documented thread-safe**，要么 **单线程 + 明确 UB**，seekdb-android 据此做 session 序列化（当前倾向后者 unless 引擎加锁） |

### 3. 语义契约（验收时建议写进引擎头文件 / 发布说明）

- **错误与 EOF**：`seekdb_last_error_code` / `seekdb_last_error` 在 rc ≠ 成功时的填充规则稳定；结果迭代结束使用**稳定 rc**（JNI 侧将某 rc 视为 EOF，且与「空行」区分方式与 [seekdb_android_jni.cpp](../../seekdb-android/src/main/cpp/seekdb_android_jni.cpp) 一致）。
- **类型 ID**：`seekdb_result_column_type_id` 与 `seekdb_row_get_*` 的选用规则固定；类型 **8 = BLOB**（与 JNI 约定一致）等需列枚举表。
- **所有权**：`SeekdbResult` / `SeekdbStmt` / `SeekdbRow` 由谁 `free`/`close`；`seekdb_stmt_close` 是否隐式释放挂起 result。
- **`seekdb_stmt_execute` 与 result**：一次 execute 是否允许多个 result 顺序、是否必须 `seekdb_result_free` 后才能再次 execute（与 reset 组合规则）。
- **BLOB 可选路径**：无 blob 导出时，JNI 走 string 缓冲绑定/读出，引擎需保证二进制安全或明确「不支持二进制列」。

### 4. ABI 版本

- Java：`SeekdbNativeBridge.nativeAbiVersion()`；JNI 内递增 **`SEEKDB_ABI_VERSION`** 的条件见本文开头。
- **breaking change** 包括：函数签名变更、句柄类型变更、rc 语义变更、wired 符号改为 optional 或删除。

### 5. 相关文档

- SQL 语言与扩展能力：[engine-features-matrix.md](engine-features-matrix.md)
- Java 行为与降级：[sqlite-android-parity-matrix.md](sqlite-android-parity-matrix.md)、[compat-contract-matrix.md](compat-contract-matrix.md)
