#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>
#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

namespace {

// Mirror seekdb.h SeekdbFieldType / SeekdbBind layout (do not include engine headers in JNI).
enum SeekdbFieldTypeJni : int32_t {
    SEEKDB_TYPE_NULL = 0,
    SEEKDB_TYPE_TINY = 1,
    SEEKDB_TYPE_SHORT = 2,
    SEEKDB_TYPE_LONG = 3,
    SEEKDB_TYPE_LONGLONG = 4,
    SEEKDB_TYPE_FLOAT = 5,
    SEEKDB_TYPE_DOUBLE = 6,
    SEEKDB_TYPE_TIME = 7,
    SEEKDB_TYPE_DATE = 8,
    SEEKDB_TYPE_DATETIME = 9,
    SEEKDB_TYPE_TIMESTAMP = 10,
    SEEKDB_TYPE_STRING = 11,
    SEEKDB_TYPE_BLOB = 12,
};

struct SeekdbBindJni {
    int32_t buffer_type;
    void* buffer;
    unsigned long buffer_length;
    unsigned long* length;
    bool* is_null;
    bool* error;
    unsigned char* is_unsigned;
};

using SeekdbHandle = void*;
using SeekdbResult = void*;
using SeekdbRow = void*;
using SeekdbStmt = void*;
using my_ulonglong = unsigned long long;

static constexpr int kSeekdbSuccess = 0;

/** Same policy as SeekdbCompatStatement.isDdlSql: avoid store_result/metadata on DDL. */
static bool jni_is_ddl_sql(const char* sql, size_t len) {
    if (sql == nullptr || len == 0) {
        return false;
    }
    size_t i = 0;
    while (i < len && (sql[i] == ' ' || sql[i] == '\t' || sql[i] == '\n' || sql[i] == '\r')) {
        ++i;
    }
    if (i + 1 < len && sql[i] == '-' && sql[i + 1] == '-') {
        const void* p = memchr(sql + i, '\n', len - i);
        if (p == nullptr) {
            return false;
        }
        i = static_cast<size_t>(static_cast<const char*>(p) - sql) + 1;
        while (i < len && (sql[i] == ' ' || sql[i] == '\t' || sql[i] == '\n' || sql[i] == '\r')) {
            ++i;
        }
    }
    if (i >= len) {
        return false;
    }
    auto match_ci = [&](const char* kw, size_t kwlen) -> bool {
        if (len - i < kwlen) {
            return false;
        }
        for (size_t k = 0; k < kwlen; ++k) {
            char a = sql[i + k];
            char b = kw[k];
            if (a >= 'A' && a <= 'Z') {
                a = static_cast<char>(a - 'A' + 'a');
            }
            if (b >= 'A' && b <= 'Z') {
                b = static_cast<char>(b - 'A' + 'a');
            }
            if (a != b) {
                return false;
            }
        }
        return true;
    };
    if (match_ci("create", 6)) {
        return true;
    }
    if (match_ci("drop", 4)) {
        return true;
    }
    if (match_ci("alter", 5)) {
        return true;
    }
    if (match_ci("truncate", 8)) {
        return true;
    }
    if (match_ci("rename", 6)) {
        return true;
    }
    return false;
}

// MySQL field types (mysql_com.h) for SeekdbField.type
namespace mysql_type {
constexpr int32_t TINY = 1;
constexpr int32_t SHORT = 2;
constexpr int32_t LONG = 3;
constexpr int32_t FLOAT = 4;
constexpr int32_t DOUBLE = 5;
constexpr int32_t LONGLONG = 8;
constexpr int32_t INT24 = 9;
constexpr int32_t YEAR = 13;
constexpr int32_t BIT = 16;
constexpr int32_t TINY_BLOB = 249;
constexpr int32_t MEDIUM_BLOB = 250;
constexpr int32_t LONG_BLOB = 251;
constexpr int32_t BLOB = 252;
} // namespace mysql_type

// Mirror seekdb.h SeekdbField up to `type` for fetch_field_direct
struct SeekdbFieldStub {
    const char* name;
    const char* org_name;
    const char* table;
    const char* org_table;
    const char* db;
    const char* catalog;
    const char* def;
    uint32_t length;
    uint32_t max_length;
    uint32_t name_length;
    uint32_t org_name_length;
    uint32_t table_length;
    uint32_t org_table_length;
    uint32_t db_length;
    uint32_t catalog_length;
    uint32_t def_length;
    uint32_t flags;
    uint32_t decimals;
    uint32_t charsetnr;
    int32_t type;
    void* extension;
};

static int mysql_type_to_category(int32_t t) {
    if (t == mysql_type::TINY || t == mysql_type::SHORT || t == mysql_type::LONG || t == mysql_type::LONGLONG
            || t == mysql_type::INT24 || t == mysql_type::YEAR) {
        return 1; // integer family -> Long
    }
    if (t == mysql_type::FLOAT || t == mysql_type::DOUBLE) {
        return 2; // Double
    }
    if (t == mysql_type::BIT) {
        return 3; // Boolean
    }
    if (t >= mysql_type::TINY_BLOB && t <= mysql_type::BLOB) {
        return 4; // BLOB -> byte[]
    }
    return 0; // string / decimal / datetime / json etc.
}

struct SeekdbDynamicApi {
    int (*seekdb_open)(const char*) = nullptr;
    int (*seekdb_open_with_service)(const char*, int) = nullptr;
    void (*seekdb_close)(void) = nullptr;
    int (*seekdb_connect)(SeekdbHandle*, const char*, bool) = nullptr;
    void (*seekdb_connect_close)(SeekdbHandle) = nullptr;
    int (*seekdb_query)(SeekdbHandle, const char*, SeekdbResult*) = nullptr;
    void (*seekdb_result_free)(SeekdbResult) = nullptr;
    unsigned int (*seekdb_num_fields)(SeekdbResult) = nullptr;
    SeekdbFieldStub* (*seekdb_fetch_field_direct)(SeekdbResult, unsigned int) = nullptr;
    size_t (*seekdb_result_column_name_len)(SeekdbResult, int32_t) = nullptr;
    int (*seekdb_result_column_name)(SeekdbResult, int32_t, char*, size_t) = nullptr;
    SeekdbRow (*seekdb_fetch_row)(SeekdbResult) = nullptr;
    bool (*seekdb_row_is_null)(SeekdbRow, int32_t) = nullptr;
    size_t (*seekdb_row_get_string_len)(SeekdbRow, int32_t) = nullptr;
    int (*seekdb_row_get_string)(SeekdbRow, int32_t, char*, size_t) = nullptr;
    int (*seekdb_row_get_int64)(SeekdbRow, int32_t, int64_t*) = nullptr;
    int (*seekdb_row_get_double)(SeekdbRow, int32_t, double*) = nullptr;
    int (*seekdb_row_get_bool)(SeekdbRow, int32_t, bool*) = nullptr;
    int (*seekdb_begin)(SeekdbHandle) = nullptr;
    int (*seekdb_commit)(SeekdbHandle) = nullptr;
    int (*seekdb_rollback)(SeekdbHandle) = nullptr;
    int (*seekdb_last_error_code)(void) = nullptr;
    const char* (*seekdb_last_error)(void) = nullptr;
    unsigned int (*seekdb_errno_fn)(SeekdbHandle) = nullptr;
    const char* (*seekdb_sqlstate_fn)(SeekdbHandle) = nullptr;

    SeekdbStmt (*seekdb_stmt_init)(SeekdbHandle) = nullptr;
    int (*seekdb_stmt_prepare)(SeekdbStmt, const char*, unsigned long) = nullptr;
    int (*seekdb_stmt_bind_param)(SeekdbStmt, SeekdbBindJni*) = nullptr;
    int (*seekdb_stmt_execute)(SeekdbStmt) = nullptr;
    int (*seekdb_stmt_store_result)(SeekdbStmt) = nullptr;
    SeekdbResult (*seekdb_stmt_result_metadata)(SeekdbStmt) = nullptr;
    void (*seekdb_stmt_close)(SeekdbStmt) = nullptr;
    unsigned long (*seekdb_stmt_param_count)(SeekdbStmt) = nullptr;
    my_ulonglong (*seekdb_stmt_affected_rows)(SeekdbStmt) = nullptr;
    my_ulonglong (*seekdb_stmt_insert_id)(SeekdbStmt) = nullptr;
    int (*seekdb_stmt_reset)(SeekdbStmt) = nullptr;

    void* lib_handle = nullptr;
    bool initialized = false;
    bool available = false;
};

SeekdbDynamicApi& api() {
    static SeekdbDynamicApi instance;
    return instance;
}

std::mutex g_stmt_mutex;
std::unordered_map<uintptr_t, std::unique_ptr<struct StmtJniState>> g_stmt_states;

struct ParamBoolSlots {
    bool is_null = false;
    bool error = false;
};

struct StmtJniState {
    SeekdbHandle connection = nullptr;
    int last_execute_rc = 0;
    std::string prepared_sql;
    unsigned long param_count = 0;
    std::vector<SeekdbBindJni> binds;
    std::vector<int64_t> long_storage;
    std::vector<double> double_storage;
    std::vector<std::string> string_storage;
    std::vector<std::vector<uint8_t>> blob_storage;
    std::vector<unsigned long> length_storage;
    std::vector<ParamBoolSlots> bool_slots;
    std::vector<unsigned char> unsigned_storage;

    void resize(unsigned long n) {
        param_count = n;
        binds.assign(n, SeekdbBindJni{});
        long_storage.assign(n, 0);
        double_storage.assign(n, 0.0);
        string_storage.assign(n, std::string());
        blob_storage.assign(n, std::vector<uint8_t>());
        length_storage.assign(n, 0);
        bool_slots.assign(n, ParamBoolSlots{});
        unsigned_storage.assign(n, 0);
        for (unsigned long i = 0; i < n; i++) {
            binds[i].buffer_type = SEEKDB_TYPE_NULL;
            binds[i].buffer = nullptr;
            binds[i].buffer_length = 0;
            binds[i].length = &length_storage[i];
            binds[i].is_null = &bool_slots[i].is_null;
            binds[i].error = &bool_slots[i].error;
            binds[i].is_unsigned = &unsigned_storage[i];
            *binds[i].is_null = true;
        }
    }
};

template <typename T>
bool load_symbol(void* handle, const char* name, T* out) {
    void* symbol = dlsym(handle, name);
    if (symbol == nullptr) {
        return false;
    }
    *out = reinterpret_cast<T>(symbol);
    return true;
}

void ensure_api_loaded() {
    auto& a = api();
    if (a.initialized) {
        return;
    }
    a.initialized = true;
    a.lib_handle = dlopen("libseekdb.so", RTLD_NOW | RTLD_LOCAL);
    if (a.lib_handle == nullptr) {
        __android_log_print(
                ANDROID_LOG_ERROR,
                "SeekdbJni",
                "dlopen libseekdb.so failed: %s",
                dlerror());
        return;
    }
    bool ok = true;
    ok = ok && load_symbol(a.lib_handle, "seekdb_open", &a.seekdb_open);
    ok = ok && load_symbol(a.lib_handle, "seekdb_open_with_service", &a.seekdb_open_with_service);
    ok = ok && load_symbol(a.lib_handle, "seekdb_close", &a.seekdb_close);
    ok = ok && load_symbol(a.lib_handle, "seekdb_connect", &a.seekdb_connect);
    ok = ok && load_symbol(a.lib_handle, "seekdb_connect_close", &a.seekdb_connect_close);
    ok = ok && load_symbol(a.lib_handle, "seekdb_query", &a.seekdb_query);
    ok = ok && load_symbol(a.lib_handle, "seekdb_result_free", &a.seekdb_result_free);
    ok = ok && load_symbol(a.lib_handle, "seekdb_num_fields", &a.seekdb_num_fields);
    ok = ok && load_symbol(a.lib_handle, "seekdb_fetch_field_direct", &a.seekdb_fetch_field_direct);
    ok = ok && load_symbol(a.lib_handle, "seekdb_result_column_name_len", &a.seekdb_result_column_name_len);
    ok = ok && load_symbol(a.lib_handle, "seekdb_result_column_name", &a.seekdb_result_column_name);
    ok = ok && load_symbol(a.lib_handle, "seekdb_fetch_row", &a.seekdb_fetch_row);
    ok = ok && load_symbol(a.lib_handle, "seekdb_row_is_null", &a.seekdb_row_is_null);
    ok = ok && load_symbol(a.lib_handle, "seekdb_row_get_string_len", &a.seekdb_row_get_string_len);
    ok = ok && load_symbol(a.lib_handle, "seekdb_row_get_string", &a.seekdb_row_get_string);
    ok = ok && load_symbol(a.lib_handle, "seekdb_row_get_int64", &a.seekdb_row_get_int64);
    ok = ok && load_symbol(a.lib_handle, "seekdb_row_get_double", &a.seekdb_row_get_double);
    ok = ok && load_symbol(a.lib_handle, "seekdb_row_get_bool", &a.seekdb_row_get_bool);
    ok = ok && load_symbol(a.lib_handle, "seekdb_begin", &a.seekdb_begin);
    ok = ok && load_symbol(a.lib_handle, "seekdb_commit", &a.seekdb_commit);
    ok = ok && load_symbol(a.lib_handle, "seekdb_rollback", &a.seekdb_rollback);
    ok = ok && load_symbol(a.lib_handle, "seekdb_last_error_code", &a.seekdb_last_error_code);
    ok = ok && load_symbol(a.lib_handle, "seekdb_last_error", &a.seekdb_last_error);
    ok = ok && load_symbol(a.lib_handle, "seekdb_stmt_init", &a.seekdb_stmt_init);
    ok = ok && load_symbol(a.lib_handle, "seekdb_stmt_prepare", &a.seekdb_stmt_prepare);
    ok = ok && load_symbol(a.lib_handle, "seekdb_stmt_bind_param", &a.seekdb_stmt_bind_param);
    ok = ok && load_symbol(a.lib_handle, "seekdb_stmt_execute", &a.seekdb_stmt_execute);
    ok = ok && load_symbol(a.lib_handle, "seekdb_stmt_store_result", &a.seekdb_stmt_store_result);
    ok = ok && load_symbol(a.lib_handle, "seekdb_stmt_result_metadata", &a.seekdb_stmt_result_metadata);
    ok = ok && load_symbol(a.lib_handle, "seekdb_stmt_close", &a.seekdb_stmt_close);
    ok = ok && load_symbol(a.lib_handle, "seekdb_stmt_param_count", &a.seekdb_stmt_param_count);
    ok = ok && load_symbol(a.lib_handle, "seekdb_stmt_affected_rows", &a.seekdb_stmt_affected_rows);
    ok = ok && load_symbol(a.lib_handle, "seekdb_stmt_insert_id", &a.seekdb_stmt_insert_id);
    a.available = ok;
    if (a.lib_handle != nullptr) {
        load_symbol(a.lib_handle, "seekdb_errno", &a.seekdb_errno_fn);
        load_symbol(a.lib_handle, "seekdb_sqlstate", &a.seekdb_sqlstate_fn);
        load_symbol(a.lib_handle, "seekdb_stmt_reset", &a.seekdb_stmt_reset);
    }
}

static StmtJniState* get_stmt_state(uintptr_t stmt_ptr) {
    std::lock_guard<std::mutex> lock(g_stmt_mutex);
    auto it = g_stmt_states.find(stmt_ptr);
    if (it == g_stmt_states.end()) {
        return nullptr;
    }
    return it->second.get();
}

static int32_t field_type_at(SeekdbResult result, jint col) {
    auto& a = api();
    if (!a.available || result == nullptr || a.seekdb_fetch_field_direct == nullptr) {
        return 0;
    }
    SeekdbFieldStub* f = a.seekdb_fetch_field_direct(result, static_cast<unsigned int>(col));
    if (f == nullptr) {
        return 0;
    }
    return f->type;
}

static jint result_column_count_impl(SeekdbResult result) {
    auto& a = api();
    if (!a.available || result == nullptr || a.seekdb_num_fields == nullptr) {
        return 0;
    }
    unsigned int nf = a.seekdb_num_fields(result);
    if (nf == static_cast<unsigned int>(-1)) {
        return 0;
    }
    return static_cast<jint>(nf);
}

static jobjectArray read_typed_row_cells(
        JNIEnv* env,
        jlong result_ptr,
        SeekdbRow row,
        jint column_count);

static jobjectArray fetch_all_typed_impl(JNIEnv* env, jlong result_ptr, jobject cancel_signal) {
    auto& a = api();
    if (!a.available || result_ptr == 0) {
        return nullptr;
    }
    jmethodID cancel_is_canceled = nullptr;
    if (cancel_signal != nullptr) {
        jclass cancel_cls = env->FindClass("android/os/CancellationSignal");
        if (cancel_cls == nullptr || env->ExceptionCheck()) {
            return nullptr;
        }
        cancel_is_canceled = env->GetMethodID(cancel_cls, "isCanceled", "()Z");
        env->DeleteLocalRef(cancel_cls);
        if (cancel_is_canceled == nullptr || env->ExceptionCheck()) {
            return nullptr;
        }
    }

    const jint column_count = result_column_count_impl(reinterpret_cast<SeekdbResult>(result_ptr));
    if (column_count <= 0) {
        return nullptr;
    }

    jclass obj_array_cls = env->FindClass("[Ljava/lang/Object;");
    if (obj_array_cls == nullptr || env->ExceptionCheck()) {
        return nullptr;
    }

    std::vector<jobjectArray> row_arrays;
    int row_index = 0;
    // Safety: misbehaving engines could yield rows forever; avoid OOM/hang in JNI.
    constexpr int kMaxFetchRows = 10000000;
    while (row_index < kMaxFetchRows) {
        if (cancel_signal != nullptr && cancel_is_canceled != nullptr) {
            if ((row_index % 32) == 0) {
                jboolean canceled = env->CallBooleanMethod(cancel_signal, cancel_is_canceled);
                if (env->ExceptionCheck()) {
                    return nullptr;
                }
                if (canceled == JNI_TRUE) {
                    jclass ex = env->FindClass("android/os/OperationCanceledException");
                    if (ex != nullptr) {
                        env->ThrowNew(ex, "Query canceled");
                    }
                    return nullptr;
                }
            }
        }
        SeekdbRow row = a.seekdb_fetch_row(reinterpret_cast<SeekdbResult>(result_ptr));
        if (row == nullptr) {
            if (a.seekdb_last_error_code != nullptr && a.seekdb_last_error_code() != kSeekdbSuccess) {
                return nullptr;
            }
            break;
        }
        jobjectArray typed_row =
                read_typed_row_cells(env, result_ptr, row, column_count);
        if (typed_row == nullptr) {
            return nullptr;
        }
        row_arrays.push_back(typed_row);
        row_index++;
    }

    jobjectArray outer =
            env->NewObjectArray(static_cast<jsize>(row_arrays.size()), obj_array_cls, nullptr);
    if (outer == nullptr || env->ExceptionCheck()) {
        return nullptr;
    }
    for (jsize r = 0; r < static_cast<jsize>(row_arrays.size()); r++) {
        env->SetObjectArrayElement(outer, r, row_arrays[static_cast<size_t>(r)]);
        env->DeleteLocalRef(row_arrays[static_cast<size_t>(r)]);
    }
    return outer;
}

static jobjectArray read_typed_row_cells(
        JNIEnv* env,
        jlong result_ptr,
        SeekdbRow row,
        jint column_count) {
    auto& a = api();
    jclass long_cls = env->FindClass("java/lang/Long");
    jclass double_cls = env->FindClass("java/lang/Double");
    jclass bool_cls = env->FindClass("java/lang/Boolean");
    jclass object_cls = env->FindClass("java/lang/Object");
    if (long_cls == nullptr || double_cls == nullptr || bool_cls == nullptr || object_cls == nullptr
            || env->ExceptionCheck()) {
        return nullptr;
    }
    jmethodID long_value_of = env->GetStaticMethodID(long_cls, "valueOf", "(J)Ljava/lang/Long;");
    jmethodID double_value_of = env->GetStaticMethodID(double_cls, "valueOf", "(D)Ljava/lang/Double;");
    jmethodID bool_value_of = env->GetStaticMethodID(bool_cls, "valueOf", "(Z)Ljava/lang/Boolean;");
    if (long_value_of == nullptr || double_value_of == nullptr || bool_value_of == nullptr
            || env->ExceptionCheck()) {
        return nullptr;
    }
    jobjectArray inner = env->NewObjectArray(column_count, object_cls, nullptr);
    if (inner == nullptr || env->ExceptionCheck()) {
        return nullptr;
    }
    for (jint i = 0; i < column_count; i++) {
        if (a.seekdb_row_is_null(row, static_cast<int32_t>(i))) {
            continue;
        }
        int32_t mysql_type = field_type_at(reinterpret_cast<SeekdbResult>(result_ptr), i);
        const int cat = mysql_type_to_category(mysql_type);
        jobject cell = nullptr;
        if (cat == 3) {
            bool v = false;
            if (a.seekdb_row_get_bool(row, static_cast<int32_t>(i), &v) == kSeekdbSuccess) {
                cell = env->CallStaticObjectMethod(
                        bool_cls,
                        bool_value_of,
                        static_cast<jboolean>(v ? JNI_TRUE : JNI_FALSE));
            }
        } else if (cat == 2) {
            double v = 0;
            if (a.seekdb_row_get_double(row, static_cast<int32_t>(i), &v) == kSeekdbSuccess) {
                cell = env->CallStaticObjectMethod(double_cls, double_value_of, static_cast<jdouble>(v));
            }
        } else if (cat == 1) {
            int64_t v = 0;
            if (a.seekdb_row_get_int64(row, static_cast<int32_t>(i), &v) == kSeekdbSuccess) {
                cell = env->CallStaticObjectMethod(long_cls, long_value_of, static_cast<jlong>(v));
            }
        } else if (cat == 4) {
            size_t blen = a.seekdb_row_get_string_len(row, static_cast<int32_t>(i));
            if (blen != static_cast<size_t>(-1) && blen > 0) {
                std::vector<uint8_t> buf(blen);
                int gr = a.seekdb_row_get_string(
                        row,
                        static_cast<int32_t>(i),
                        reinterpret_cast<char*>(buf.data()),
                        blen + 1);
                if (gr == kSeekdbSuccess) {
                    jbyteArray jbytes = env->NewByteArray(static_cast<jsize>(blen));
                    if (jbytes != nullptr) {
                        env->SetByteArrayRegion(
                                jbytes, 0, static_cast<jsize>(blen),
                                reinterpret_cast<const jbyte*>(buf.data()));
                        cell = jbytes;
                    }
                }
            }
        } else {
            size_t len = a.seekdb_row_get_string_len(row, static_cast<int32_t>(i));
            std::string value(len + 1, '\0');
            if (a.seekdb_row_get_string(row, static_cast<int32_t>(i), value.data(), value.size()) == kSeekdbSuccess) {
                cell = env->NewStringUTF(value.c_str());
            }
        }
        if (cell != nullptr) {
            env->SetObjectArrayElement(inner, i, cell);
            env->DeleteLocalRef(cell);
        }
    }
    return inner;
}

} // namespace

extern "C" JNIEXPORT jint JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeAbiVersion(JNIEnv*, jclass) {
    // First published JNI/engine contract for seekdb-android (see docs/seekdb-android/abi-parity-checklist.md).
    return 1;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeIsAvailable(JNIEnv*, jclass) {
    ensure_api_loaded();
    return api().available ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeOpen(
        JNIEnv* env,
        jclass,
        jstring db_dir,
        jint port) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available) {
        return -1;
    }
    const char* db_path = db_dir == nullptr ? "" : env->GetStringUTFChars(db_dir, nullptr);
    int rc;
    if (port <= 0) {
        rc = a.seekdb_open(db_path);
    } else {
        rc = a.seekdb_open_with_service(db_path, static_cast<int>(port));
    }
    if (db_dir != nullptr) {
        env->ReleaseStringUTFChars(db_dir, db_path);
    }
    return static_cast<jint>(rc);
}

extern "C" JNIEXPORT void JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeClose(JNIEnv*, jclass) {
    ensure_api_loaded();
    auto& a = api();
    if (a.available) {
        a.seekdb_close();
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeConnect(
        JNIEnv* env,
        jclass,
        jstring database,
        jboolean autocommit) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available) {
        return 0;
    }
    const char* db_name = database == nullptr ? "" : env->GetStringUTFChars(database, nullptr);
    SeekdbHandle conn = nullptr;
    int rc = a.seekdb_connect(&conn, db_name, autocommit == JNI_TRUE);
    if (database != nullptr) {
        env->ReleaseStringUTFChars(database, db_name);
    }
    if (rc != kSeekdbSuccess || conn == nullptr) {
        return 0;
    }
    return reinterpret_cast<jlong>(conn);
}

extern "C" JNIEXPORT void JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeDisconnect(JNIEnv*, jclass, jlong connection_ptr) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available) {
        return;
    }
    if (connection_ptr != 0) {
        a.seekdb_connect_close(reinterpret_cast<SeekdbHandle>(connection_ptr));
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeQuery(
        JNIEnv* env,
        jclass,
        jlong connection_ptr,
        jstring sql) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available) {
        return 0;
    }
    if (connection_ptr == 0 || sql == nullptr) {
        return 0;
    }
    const char* sql_cstr = env->GetStringUTFChars(sql, nullptr);
    SeekdbResult result = nullptr;
    int rc = a.seekdb_query(reinterpret_cast<SeekdbHandle>(connection_ptr), sql_cstr, &result);
    env->ReleaseStringUTFChars(sql, sql_cstr);
    if (rc != kSeekdbSuccess || result == nullptr) {
        return 0;
    }
    return reinterpret_cast<jlong>(result);
}

extern "C" JNIEXPORT void JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeResultFree(JNIEnv*, jclass, jlong result_ptr) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available) {
        return;
    }
    if (result_ptr != 0) {
        a.seekdb_result_free(reinterpret_cast<SeekdbResult>(result_ptr));
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeBegin(JNIEnv*, jclass, jlong connection_ptr) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available || connection_ptr == 0) {
        return -1;
    }
    return static_cast<jint>(a.seekdb_begin(reinterpret_cast<SeekdbHandle>(connection_ptr)));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeCommit(JNIEnv*, jclass, jlong connection_ptr) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available || connection_ptr == 0) {
        return -1;
    }
    return static_cast<jint>(a.seekdb_commit(reinterpret_cast<SeekdbHandle>(connection_ptr)));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeRollback(JNIEnv*, jclass, jlong connection_ptr) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available || connection_ptr == 0) {
        return -1;
    }
    return static_cast<jint>(a.seekdb_rollback(reinterpret_cast<SeekdbHandle>(connection_ptr)));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeResultColumnCount(JNIEnv*, jclass, jlong result_ptr) {
    ensure_api_loaded();
    if (!api().available || result_ptr == 0) {
        return 0;
    }
    return result_column_count_impl(reinterpret_cast<SeekdbResult>(result_ptr));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeResultColumnName(
        JNIEnv* env,
        jclass,
        jlong result_ptr,
        jint column_index) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available || result_ptr == 0 || column_index < 0) {
        return nullptr;
    }
    size_t len = a.seekdb_result_column_name_len(
            reinterpret_cast<SeekdbResult>(result_ptr),
            static_cast<int32_t>(column_index));
    if (len == static_cast<size_t>(-1)) {
        return nullptr;
    }
    std::string buf(len + 1, '\0');
    int rc = a.seekdb_result_column_name(
            reinterpret_cast<SeekdbResult>(result_ptr),
            static_cast<int32_t>(column_index),
            buf.data(),
            buf.size());
    if (rc != kSeekdbSuccess) {
        return nullptr;
    }
    return env->NewStringUTF(buf.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeResultColumnTypeId(
        JNIEnv*,
        jclass,
        jlong result_ptr,
        jint column_index) {
    ensure_api_loaded();
    if (!api().available || result_ptr == 0 || column_index < 0) {
        return 0;
    }
    return static_cast<jint>(field_type_at(reinterpret_cast<SeekdbResult>(result_ptr), column_index));
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeResultReadNextRowTyped(
        JNIEnv* env,
        jclass,
        jlong result_ptr) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available || result_ptr == 0) {
        return nullptr;
    }
    SeekdbRow row = a.seekdb_fetch_row(reinterpret_cast<SeekdbResult>(result_ptr));
    if (row == nullptr) {
        if (a.seekdb_last_error_code != nullptr && a.seekdb_last_error_code() != kSeekdbSuccess) {
            return nullptr;
        }
        jclass oc = env->FindClass("java/lang/Object");
        if (oc == nullptr) {
            return nullptr;
        }
        return env->NewObjectArray(0, oc, nullptr);
    }
    const jint column_count = result_column_count_impl(reinterpret_cast<SeekdbResult>(result_ptr));
    if (column_count <= 0) {
        return nullptr;
    }
    return read_typed_row_cells(env, result_ptr, row, column_count);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeResultFetchAll(
        JNIEnv* env,
        jclass,
        jlong result_ptr) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available || result_ptr == 0) {
        return nullptr;
    }

    const jint column_count = result_column_count_impl(reinterpret_cast<SeekdbResult>(result_ptr));
    if (column_count <= 0) {
        return nullptr;
    }

    std::vector<std::vector<std::string>> rows;
    std::vector<std::vector<bool>> row_nulls;
    while (true) {
        SeekdbRow row = a.seekdb_fetch_row(reinterpret_cast<SeekdbResult>(result_ptr));
        if (row == nullptr) {
            if (a.seekdb_last_error_code != nullptr && a.seekdb_last_error_code() != kSeekdbSuccess) {
                break;
            }
            break;
        }
        std::vector<std::string> out_row(column_count);
        std::vector<bool> out_nulls(column_count, false);
        for (jint i = 0; i < column_count; i++) {
            if (a.seekdb_row_is_null(row, static_cast<int32_t>(i))) {
                out_nulls[static_cast<size_t>(i)] = true;
                continue;
            }
            size_t len = a.seekdb_row_get_string_len(row, static_cast<int32_t>(i));
            std::string value(len + 1, '\0');
            int get_rc = a.seekdb_row_get_string(row, static_cast<int32_t>(i), value.data(), value.size());
            if (get_rc == kSeekdbSuccess) {
                out_row[static_cast<size_t>(i)] = value.c_str();
            }
        }
        rows.emplace_back(std::move(out_row));
        row_nulls.emplace_back(std::move(out_nulls));
    }

    jclass string_class = env->FindClass("java/lang/String");
    jobjectArray outer = env->NewObjectArray(static_cast<jsize>(rows.size()), env->FindClass("[Ljava/lang/String;"), nullptr);
    for (jsize r = 0; r < static_cast<jsize>(rows.size()); r++) {
        jobjectArray inner = env->NewObjectArray(column_count, string_class, nullptr);
        for (jint c = 0; c < column_count; c++) {
            if (!row_nulls[static_cast<size_t>(r)][static_cast<size_t>(c)]) {
                jstring value = env->NewStringUTF(rows[static_cast<size_t>(r)][static_cast<size_t>(c)].c_str());
                env->SetObjectArrayElement(inner, c, value);
                env->DeleteLocalRef(value);
            }
        }
        env->SetObjectArrayElement(outer, r, inner);
        env->DeleteLocalRef(inner);
    }
    return outer;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeResultFetchAllTyped(JNIEnv* env, jclass, jlong result_ptr) {
    ensure_api_loaded();
    return fetch_all_typed_impl(env, result_ptr, nullptr);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeResultFetchAllTyped__JLandroid_os_CancellationSignal_2(
        JNIEnv* env,
        jclass,
        jlong result_ptr,
        jobject cancel_signal) {
    ensure_api_loaded();
    return fetch_all_typed_impl(env, result_ptr, cancel_signal);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeStmtPrepare(
        JNIEnv* env,
        jclass,
        jlong connection_ptr,
        jstring sql) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available || connection_ptr == 0 || sql == nullptr) {
        return 0;
    }
    SeekdbStmt stmt = a.seekdb_stmt_init(reinterpret_cast<SeekdbHandle>(connection_ptr));
    if (stmt == nullptr) {
        return 0;
    }
    const char* sql_c = env->GetStringUTFChars(sql, nullptr);
    unsigned long sql_len = static_cast<unsigned long>(strlen(sql_c));
    int rc = a.seekdb_stmt_prepare(stmt, sql_c, sql_len);
    env->ReleaseStringUTFChars(sql, sql_c);
    if (rc != kSeekdbSuccess) {
        a.seekdb_stmt_close(stmt);
        return 0;
    }
    unsigned long pc = a.seekdb_stmt_param_count != nullptr ? a.seekdb_stmt_param_count(stmt) : 0;
    auto state = std::make_unique<StmtJniState>();
    state->connection = reinterpret_cast<SeekdbHandle>(connection_ptr);
    state->prepared_sql.assign(sql_c, sql_len);
    state->resize(pc);
    {
        std::lock_guard<std::mutex> lock(g_stmt_mutex);
        g_stmt_states[reinterpret_cast<uintptr_t>(stmt)] = std::move(state);
    }
    return reinterpret_cast<jlong>(stmt);
}

extern "C" JNIEXPORT void JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeStmtClose(JNIEnv*, jclass, jlong stmt_ptr) {
    ensure_api_loaded();
    auto& a = api();
    if (stmt_ptr != 0) {
        {
            std::lock_guard<std::mutex> lock(g_stmt_mutex);
            g_stmt_states.erase(static_cast<uintptr_t>(stmt_ptr));
        }
        if (a.available) {
            a.seekdb_stmt_close(reinterpret_cast<SeekdbStmt>(stmt_ptr));
        }
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeStmtReset(JNIEnv*, jclass, jlong stmt_ptr) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available || stmt_ptr == 0 || a.seekdb_stmt_reset == nullptr) {
        return -1;
    }
    StmtJniState* st = get_stmt_state(static_cast<uintptr_t>(stmt_ptr));
    if (st != nullptr) {
        st->resize(st->param_count);
    }
    return static_cast<jint>(a.seekdb_stmt_reset(reinterpret_cast<SeekdbStmt>(stmt_ptr)));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeStmtClearBindings(JNIEnv*, jclass, jlong stmt_ptr) {
    StmtJniState* st = get_stmt_state(static_cast<uintptr_t>(stmt_ptr));
    if (st == nullptr) {
        return -1;
    }
    st->resize(st->param_count);
    return kSeekdbSuccess;
}

static jint bind_slot_prepare(StmtJniState* st, unsigned int index) {
    if (st == nullptr || st->param_count == 0 || index >= st->param_count) {
        return -1;
    }
    SeekdbBindJni& b = st->binds[index];
    *b.is_null = false;
    b.buffer = nullptr;
    b.buffer_length = 0;
    *b.length = 0;
    return kSeekdbSuccess;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeStmtBindNull(JNIEnv*, jclass, jlong stmt_ptr, jint index) {
    ensure_api_loaded();
    StmtJniState* st = get_stmt_state(static_cast<uintptr_t>(stmt_ptr));
    if (!api().available || st == nullptr || index < 0
            || static_cast<unsigned long>(index) >= st->param_count) {
        return -1;
    }
    SeekdbBindJni& b = st->binds[static_cast<unsigned int>(index)];
    b.buffer_type = SEEKDB_TYPE_NULL;
    b.buffer = nullptr;
    b.buffer_length = 0;
    *b.is_null = true;
    *b.length = 0;
    return kSeekdbSuccess;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeStmtBindLong(JNIEnv*, jclass, jlong stmt_ptr, jint index, jlong v) {
    ensure_api_loaded();
    StmtJniState* st = get_stmt_state(static_cast<uintptr_t>(stmt_ptr));
    if (!api().available || st == nullptr || index < 0
            || static_cast<unsigned long>(index) >= st->param_count) {
        return -1;
    }
    bind_slot_prepare(st, static_cast<unsigned int>(index));
    unsigned int i = static_cast<unsigned int>(index);
    st->long_storage[i] = static_cast<int64_t>(v);
    SeekdbBindJni& b = st->binds[i];
    b.buffer_type = SEEKDB_TYPE_LONGLONG;
    b.buffer = &st->long_storage[i];
    b.buffer_length = sizeof(int64_t);
    *b.length = sizeof(int64_t);
    return kSeekdbSuccess;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeStmtBindDouble(JNIEnv*, jclass, jlong stmt_ptr, jint index, jdouble v) {
    ensure_api_loaded();
    StmtJniState* st = get_stmt_state(static_cast<uintptr_t>(stmt_ptr));
    if (!api().available || st == nullptr || index < 0
            || static_cast<unsigned long>(index) >= st->param_count) {
        return -1;
    }
    bind_slot_prepare(st, static_cast<unsigned int>(index));
    unsigned int i = static_cast<unsigned int>(index);
    st->double_storage[i] = static_cast<double>(v);
    SeekdbBindJni& b = st->binds[i];
    b.buffer_type = SEEKDB_TYPE_DOUBLE;
    b.buffer = &st->double_storage[i];
    b.buffer_length = sizeof(double);
    *b.length = sizeof(double);
    return kSeekdbSuccess;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeStmtBindString(
        JNIEnv* env,
        jclass,
        jlong stmt_ptr,
        jint index,
        jstring value_str) {
    ensure_api_loaded();
    StmtJniState* st = get_stmt_state(static_cast<uintptr_t>(stmt_ptr));
    if (!api().available || st == nullptr || index < 0 || value_str == nullptr
            || static_cast<unsigned long>(index) >= st->param_count) {
        return -1;
    }
    bind_slot_prepare(st, static_cast<unsigned int>(index));
    unsigned int i = static_cast<unsigned int>(index);
    const char* s = env->GetStringUTFChars(value_str, nullptr);
    st->string_storage[i].assign(s);
    env->ReleaseStringUTFChars(value_str, s);
    SeekdbBindJni& b = st->binds[i];
    b.buffer_type = SEEKDB_TYPE_STRING;
    b.buffer = st->string_storage[i].empty() ? const_cast<char*>("") : &st->string_storage[i][0];
    *b.length = static_cast<unsigned long>(st->string_storage[i].size());
    b.buffer_length = *b.length + 1;
    return kSeekdbSuccess;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeStmtBindBlob(
        JNIEnv* env,
        jclass,
        jlong stmt_ptr,
        jint index,
        jbyteArray value_blob) {
    ensure_api_loaded();
    StmtJniState* st = get_stmt_state(static_cast<uintptr_t>(stmt_ptr));
    if (!api().available || st == nullptr || index < 0 || value_blob == nullptr
            || static_cast<unsigned long>(index) >= st->param_count) {
        return -1;
    }
    bind_slot_prepare(st, static_cast<unsigned int>(index));
    unsigned int i = static_cast<unsigned int>(index);
    jsize len = env->GetArrayLength(value_blob);
    jbyte* data = env->GetByteArrayElements(value_blob, nullptr);
    st->blob_storage[i].assign(
            reinterpret_cast<const uint8_t*>(data),
            reinterpret_cast<const uint8_t*>(data) + len);
    env->ReleaseByteArrayElements(value_blob, data, JNI_ABORT);
    SeekdbBindJni& b = st->binds[i];
    b.buffer_type = SEEKDB_TYPE_BLOB;
    b.buffer = st->blob_storage[i].empty() ? nullptr : st->blob_storage[i].data();
    *b.length = static_cast<unsigned long>(st->blob_storage[i].size());
    b.buffer_length = *b.length;
    return kSeekdbSuccess;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeStmtLastExecuteRc(JNIEnv*, jclass, jlong stmt_ptr) {
    ensure_api_loaded();
    if (stmt_ptr == 0) {
        return 0;
    }
    StmtJniState* st = get_stmt_state(static_cast<uintptr_t>(stmt_ptr));
    if (st == nullptr) {
        return 0;
    }
    return static_cast<jint>(st->last_execute_rc);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeStmtExecute(JNIEnv*, jclass, jlong stmt_ptr) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available || stmt_ptr == 0) {
        return 0;
    }
    StmtJniState* st = get_stmt_state(static_cast<uintptr_t>(stmt_ptr));
    if (st == nullptr) {
        return 0;
    }
    SeekdbStmt stmt = reinterpret_cast<SeekdbStmt>(stmt_ptr);
    if (st->param_count > 0 && a.seekdb_stmt_bind_param != nullptr) {
        int brc = a.seekdb_stmt_bind_param(stmt, st->binds.data());
        if (brc != kSeekdbSuccess) {
            st->last_execute_rc = brc;
            return 0;
        }
    }
    int rc = a.seekdb_stmt_execute(stmt);
    st->last_execute_rc = rc;
    if (rc != kSeekdbSuccess) {
        return 0;
    }
    if (jni_is_ddl_sql(st->prepared_sql.data(), st->prepared_sql.size())) {
        return 0;
    }
    if (a.seekdb_stmt_store_result != nullptr) {
        a.seekdb_stmt_store_result(stmt);
    }
    SeekdbResult meta = nullptr;
    if (a.seekdb_stmt_result_metadata != nullptr) {
        meta = a.seekdb_stmt_result_metadata(stmt);
    }
    return reinterpret_cast<jlong>(meta);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeStmtAffectedRows(JNIEnv*, jclass, jlong stmt_ptr) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available || stmt_ptr == 0) {
        return 0;
    }
    return static_cast<jlong>(a.seekdb_stmt_affected_rows(reinterpret_cast<SeekdbStmt>(stmt_ptr)));
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeStmtInsertId(JNIEnv*, jclass, jlong stmt_ptr) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available || stmt_ptr == 0) {
        return 0;
    }
    return static_cast<jlong>(a.seekdb_stmt_insert_id(reinterpret_cast<SeekdbStmt>(stmt_ptr)));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeLastErrorCode(JNIEnv*, jclass) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available || a.seekdb_last_error_code == nullptr) {
        return 0;
    }
    return static_cast<jint>(a.seekdb_last_error_code());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeErrno(JNIEnv*, jclass, jlong connection_ptr) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available || connection_ptr == 0 || a.seekdb_errno_fn == nullptr) {
        return 0;
    }
    const unsigned int u = a.seekdb_errno_fn(reinterpret_cast<SeekdbHandle>(connection_ptr));
    return static_cast<jint>(static_cast<int32_t>(u));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeLastErrorMessage(JNIEnv* env, jclass) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available || a.seekdb_last_error == nullptr) {
        return env->NewStringUTF("");
    }
    const char* msg = a.seekdb_last_error();
    return env->NewStringUTF(msg == nullptr ? "" : msg);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeLastSqlState(JNIEnv* env, jclass) {
    ensure_api_loaded();
    (void)env;
    return env->NewStringUTF("");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeLastSqlState__J(JNIEnv* env, jclass, jlong connection_ptr) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available || a.seekdb_sqlstate_fn == nullptr || connection_ptr == 0) {
        return env->NewStringUTF("");
    }
    const char* st = a.seekdb_sqlstate_fn(reinterpret_cast<SeekdbHandle>(connection_ptr));
    return env->NewStringUTF(st == nullptr ? "" : st);
}
