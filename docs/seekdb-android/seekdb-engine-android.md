# SeekDB 引擎（seekdb）与 Android 嵌入式集成说明

本文说明在 **Android app 进程内** 加载 `libseekdb.so` 时，**seekdb 仓库**与 **seekdb-android** 侧为稳定运行而保留的必要修改，以及如何编译与验证。

## 1. 修改分类（便于评审与排障）

| 类别 | 含义 |
|------|------|
| **集成排障（RCA）** | 与某次具体问题链强相关（日志、堆栈、仪器测试复现）。 |
| **Android embed 加固** | 不依赖单条 log，在真机/模拟器上 bring-up 时通常会单独遇到。 |
| **C ABI / 语义** | 语句或连接路径在 API 层应如何走读/写，与某次 crash 不一定一一对应。 |

## 2. seekdb 仓库（典型 `feat/embedded-mode` / Android 交叉编译）

以下均为在 **Android 目标**（`__ANDROID__` 或进程模型）下有意义；具体文件以当前分支为准。

### 2.1 集成排障（RCA）

- **`src/storage/ls/ob_ls_tablet_service.cpp`**
  - **`report_tablet_to_rs`**：嵌入式 Android **无 RS**，向 `ObTabletTableUpdater` 提交上报可能引发进程异常退出 → 在 `__ANDROID__` 下对该路径 **直接 return**。
  - **`insert_tablet_rows` + 主键冲突**：与 **`INSERT IGNORE`** / `is_ignore_` 配合，避免与 Room/SupportSQLite 的 ignore 语义不一致（与 **`src/sql/engine/dml/ob_dml_service.cpp`** 中 `dml_param.is_ignore_ = base_ctdef.is_ignore_` 配套）。
- **`src/share/schema/ob_ddl_sql_service.cpp`**
  - 大段 DDL 写入 `__all_ddl_operation` 在嵌入式上易触发 **LOB/大文本路径问题** → `__ANDROID__` 下对参与落库的 DDL 文本使用 **空串**（仍保留元数据/流程所需字段，按实现为准）。

### 2.2 Android embed 加固

- **`deps/oblib/.../ob_cpu_topology.cpp`**
  - Android 同时定义 `__linux__`，但 app 进程内用 **`system()` + `grep /proc/cpuinfo`** 探测 CPU 特性不安全或不可靠 → Android 上改为 **`init_from_cpu`** 等直接探测路径。
- **`deps/oblib/.../ob_signal_handlers.cpp`**
  - App 进程内 **ART / libsigchain** 已接管致命信号；再安装 OceanBase 全局信号处理易与 JNI/调试冲突 → **`install_ob_signal_handler` 在 `__ANDROID__` 上尽早 return**。

### 2.3 C ABI / 语义（seekdb 对外 C API）

- **`src/include/seekdb.cpp`（`libseekdb`）**
  - **`seekdb_stmt_execute`**：对判定为 **写 SQL**（如 `INSERT`/`UPDATE`/`DELETE` 等）的路径走 **`seekdb_execute_update`**，而不是一律 **`seekdb_query`**，以保证预编译语句执行 DML 的语义与测试一致。

## 3. seekdb-android 仓库（库与仪器测试）

- **`SeekdbCompatDatabase.beginTransaction`**：与 Android `SQLiteDatabase` 一致，**允许嵌套 begin**（仅最外层对引擎发 `BEGIN`）；单元测试中已移除与旧行为（禁止嵌套）绑定的用例。
- **`seekdb_android_jni.cpp`**：`dlopen("libseekdb.so")` 失败时 **`__android_log_print`（ERROR）**，便于区分「库未打包」与引擎内部错误。
- **`build.gradle`**：`androidTest` 中 **Room** 依赖需 **`lifecycle-livedata`**、**`room-paging`** 以补全注解处理器 classpath（与 Room 仪器测试一致）。

`libseekdb.so` 默认 **不提交**（见仓库根 `.gitignore` 的 `jniLibs` 规则）；本地需从 seekdb 的 Android 构建产物拷贝，见下节。

## 4. 编译 seekdb（Android `libseekdb.so`）

在 seekdb 仓库（与 seekdb-android 同级克隆时）文档 [docs/developer-guide/zh/android.md](../../../seekdb/docs/developer-guide/zh/android.md) 中：

1. `./build.sh release --android --init`（依赖初始化）
2. `cd build_android_release && make libseekdb -j$(nproc)`

产物路径一般为：

`build_android_release/src/include/libseekdb.so`

将 `libseekdb.so` 放到 seekdb-android 模块：

`seekdb-android/src/main/jniLibs/arm64-v8a/libseekdb.so`（当前集成以 **arm64-v8a** 为主；其它 ABI 按团队流程同步）。

## 5. 验证 seekdb-android

```bash
./gradlew :seekdb-android:testDebugUnitTest
./gradlew :seekdb-android:connectedDebugAndroidTest
```

### 5.1 仪器测试与「冷启动」/脏数据

- 仪器测试包名一般为 **`com.oceanbase.seekdb.android.test`**。
- 若更换过 **`libseekdb.so`** 或出现过 **schema 版本错乱**（例如 migration 测试报 **downgrade**），建议先清数据再跑：

```bash
adb shell pm clear com.oceanbase.seekdb.android.test
```

再执行 `connectedDebugAndroidTest` 或：

```bash
adb shell am instrument -w com.oceanbase.seekdb.android.test/androidx.test.runner.AndroidJUnitRunner
```

## 6. 相关文档

- [local-dev-guide.md](local-dev-guide.md)：本地构建与测试命令。
- [implementation-status.md](implementation-status.md)：能力清单与后续事项。
- [abi-parity-checklist.md](abi-parity-checklist.md)：`libseekdb.so` 与 JNI 的 C ABI 约定。
