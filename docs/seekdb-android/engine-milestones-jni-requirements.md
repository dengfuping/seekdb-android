# SeekDB 引擎里程碑与 seekdb-android JNI 需求清单

本文档与仓库内 **SQLite parity + Inspector** 路线图（Phase 1 / Phase 5 及引擎边界）一致，用于与 SeekDB 团队对齐交付节奏。

## 1. 多连接与会话语义

| 里程碑 | SeekDB / C API 期望 | JNI / Java 依赖 |
|--------|---------------------|-----------------|
| M1 | 文档化：单进程内是否允许多个 `seekdb_connect`、是否读写隔离 | 维持 `SeekdbConnectionPool` 单主连接 + 对称 acquire/release；多连接时扩展 `installPrimary` 为多槽或连接工厂 |
| M2 | 读会话与写会话的隔离策略（若仅单连接，则引擎侧队列 vs Java 侧队列） | `SeekdbSerializedSession` + `SeekdbRuntime.globalExclusiveLock()` 协作式串行 |

## 2. 取消 / 中断

| 里程碑 | SeekDB 期望 | 现状 |
|--------|-------------|------|
| M3 | 语句级或连接级 `seekdb_*_interrupt` / kill（若提供） | JNI 在 `nativeResultFetchAllTyped` 路径轮询 `CancellationSignal` |
| M4 | 流式 `nativeResultReadNextRowTyped` 全路径可中断 | 需在 JNI 循环中轮询取消位；引擎若支持异步取消则升级 |

## 3. 触发器与 DDL 稳定性

| 里程碑 | 说明 |
|--------|------|
| M5 | Room 失效依赖 `CREATE TRIGGER` + `room_table_modification_log`；需引擎在嵌入模式下对触发器 + DDL 长期稳定（崩溃/升级路径） |
| M6 | 与 [room-sqlite-compat.md](room-sqlite-compat.md) §6 一致；Java 侧通过 `SeekdbCompatSql` 改写 SQL，不跳过触发器 |

## 4. PRAGMA 与会话等价

| 里程碑 | 说明 |
|--------|------|
| M7 | `PRAGMA foreign_keys` / `journal_mode` 等映射到 OceanBase 会话变量或等价 SQL；见 [engine-features-matrix.md](engine-features-matrix.md) |
| M8 | 无法映射的 PRAGMA 在 compat 层显式 no-op / Unsupported 并写入矩阵 |

## 5. JNI ABI

- 破坏性变更时递增 `SeekdbNativeBridge.nativeAbiVersion()` 与 [abi-parity-checklist.md](abi-parity-checklist.md)。

## 修订记录

- 随 `libseekdb.so` / JNI 能力更新同步本表。
