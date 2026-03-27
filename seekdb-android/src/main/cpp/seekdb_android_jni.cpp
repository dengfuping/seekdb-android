#include <jni.h>
#include <dlfcn.h>
#include <atomic>
#include <string>
#include <vector>
#include <cstdint>
#include <cstddef>
#include <cstring>

namespace {
std::atomic<long> g_next_handle{1};

struct SeekdbDynamicApi {
    using SeekdbConnection = void*;
    using SeekdbResult = void*;
    using SeekdbRow = void*;
    using SeekdbStmt = void*;
    using SeekdbValue = void*;

    int (*seekdb_open)(const char*, int) = nullptr;
    void (*seekdb_close)(void) = nullptr;
    int (*seekdb_connect)(SeekdbConnection*, const char*, bool) = nullptr;
    void (*seekdb_disconnect)(SeekdbConnection) = nullptr;
    int (*seekdb_query_cstr)(SeekdbConnection, const char*, SeekdbResult*) = nullptr;
    void (*seekdb_result_free)(SeekdbResult) = nullptr;
    int (*seekdb_begin)(SeekdbConnection) = nullptr;
    int (*seekdb_commit)(SeekdbConnection) = nullptr;
    int (*seekdb_rollback)(SeekdbConnection) = nullptr;
    int (*seekdb_result_column_count)(SeekdbResult, int64_t*) = nullptr;
    int (*seekdb_result_column_type_id)(SeekdbResult, int64_t, int32_t*) = nullptr;
    size_t (*seekdb_result_column_name_len)(SeekdbResult, int32_t) = nullptr;
    int (*seekdb_result_column_name)(SeekdbResult, int32_t, char*, size_t) = nullptr;
    int (*seekdb_result_row_next)(SeekdbResult, SeekdbRow*) = nullptr;
    bool (*seekdb_row_is_null)(SeekdbRow, int32_t) = nullptr;
    size_t (*seekdb_row_get_string_len)(SeekdbRow, int32_t) = nullptr;
    int (*seekdb_row_get_string)(SeekdbRow, int32_t, char*, size_t) = nullptr;
    int (*seekdb_row_get_int64)(SeekdbRow, int32_t, int64_t*) = nullptr;
    int (*seekdb_row_get_double)(SeekdbRow, int32_t, double*) = nullptr;
    int (*seekdb_row_get_bool)(SeekdbRow, int32_t, bool*) = nullptr;
    int (*seekdb_stmt_prepare)(SeekdbConnection, const char*, size_t, SeekdbStmt*) = nullptr;
    void (*seekdb_stmt_close)(SeekdbStmt) = nullptr;
    int (*seekdb_stmt_bind_value)(SeekdbStmt, unsigned int, SeekdbValue) = nullptr;
    int (*seekdb_stmt_execute)(SeekdbStmt, SeekdbResult*) = nullptr;
    unsigned long long (*seekdb_stmt_affected_rows)(SeekdbStmt) = nullptr;
    unsigned long long (*seekdb_stmt_insert_id)(SeekdbStmt) = nullptr;
    int (*seekdb_last_error_code)(void) = nullptr;
    const char* (*seekdb_last_error)(void) = nullptr;
    SeekdbValue (*seekdb_value_alloc)(void) = nullptr;
    void (*seekdb_value_free)(SeekdbValue) = nullptr;
    void (*seekdb_value_set_null)(SeekdbValue) = nullptr;
    int (*seekdb_value_set_int64)(SeekdbValue, int64_t) = nullptr;
    int (*seekdb_value_set_double)(SeekdbValue, double) = nullptr;
    int (*seekdb_value_set_string)(SeekdbValue, const char*, size_t) = nullptr;
    int (*seekdb_value_set_blob)(SeekdbValue, const void*, size_t) = nullptr;
    size_t (*seekdb_row_get_blob_len)(SeekdbRow, int32_t) = nullptr;
    int (*seekdb_row_get_blob)(SeekdbRow, int32_t, uint8_t*, size_t) = nullptr;
    const char* (*seekdb_sqlstate)(void) = nullptr;
    int (*seekdb_stmt_reset)(SeekdbStmt) = nullptr;
    int (*seekdb_stmt_clear_bindings)(SeekdbStmt) = nullptr;
    void* lib_handle = nullptr;
    bool initialized = false;
    bool available = false;
};

SeekdbDynamicApi& api() {
    static SeekdbDynamicApi instance;
    return instance;
}

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
        return;
    }
    bool ok = true;
    ok = ok && load_symbol(a.lib_handle, "seekdb_open", &a.seekdb_open);
    ok = ok && load_symbol(a.lib_handle, "seekdb_close", &a.seekdb_close);
    ok = ok && load_symbol(a.lib_handle, "seekdb_connect", &a.seekdb_connect);
    ok = ok && load_symbol(a.lib_handle, "seekdb_disconnect", &a.seekdb_disconnect);
    ok = ok && load_symbol(a.lib_handle, "seekdb_query_cstr", &a.seekdb_query_cstr);
    ok = ok && load_symbol(a.lib_handle, "seekdb_result_free", &a.seekdb_result_free);
    ok = ok && load_symbol(a.lib_handle, "seekdb_begin", &a.seekdb_begin);
    ok = ok && load_symbol(a.lib_handle, "seekdb_commit", &a.seekdb_commit);
    ok = ok && load_symbol(a.lib_handle, "seekdb_rollback", &a.seekdb_rollback);
    ok = ok && load_symbol(a.lib_handle, "seekdb_result_column_count", &a.seekdb_result_column_count);
    ok = ok && load_symbol(a.lib_handle, "seekdb_result_column_type_id", &a.seekdb_result_column_type_id);
    ok = ok && load_symbol(a.lib_handle, "seekdb_result_column_name_len", &a.seekdb_result_column_name_len);
    ok = ok && load_symbol(a.lib_handle, "seekdb_result_column_name", &a.seekdb_result_column_name);
    ok = ok && load_symbol(a.lib_handle, "seekdb_result_row_next", &a.seekdb_result_row_next);
    ok = ok && load_symbol(a.lib_handle, "seekdb_row_is_null", &a.seekdb_row_is_null);
    ok = ok && load_symbol(a.lib_handle, "seekdb_row_get_string_len", &a.seekdb_row_get_string_len);
    ok = ok && load_symbol(a.lib_handle, "seekdb_row_get_string", &a.seekdb_row_get_string);
    ok = ok && load_symbol(a.lib_handle, "seekdb_row_get_int64", &a.seekdb_row_get_int64);
    ok = ok && load_symbol(a.lib_handle, "seekdb_row_get_double", &a.seekdb_row_get_double);
    ok = ok && load_symbol(a.lib_handle, "seekdb_row_get_bool", &a.seekdb_row_get_bool);
    ok = ok && load_symbol(a.lib_handle, "seekdb_stmt_prepare", &a.seekdb_stmt_prepare);
    ok = ok && load_symbol(a.lib_handle, "seekdb_stmt_close", &a.seekdb_stmt_close);
    ok = ok && load_symbol(a.lib_handle, "seekdb_stmt_bind_value", &a.seekdb_stmt_bind_value);
    ok = ok && load_symbol(a.lib_handle, "seekdb_stmt_execute", &a.seekdb_stmt_execute);
    ok = ok && load_symbol(a.lib_handle, "seekdb_stmt_affected_rows", &a.seekdb_stmt_affected_rows);
    ok = ok && load_symbol(a.lib_handle, "seekdb_stmt_insert_id", &a.seekdb_stmt_insert_id);
    ok = ok && load_symbol(a.lib_handle, "seekdb_last_error_code", &a.seekdb_last_error_code);
    ok = ok && load_symbol(a.lib_handle, "seekdb_last_error", &a.seekdb_last_error);
    ok = ok && load_symbol(a.lib_handle, "seekdb_value_alloc", &a.seekdb_value_alloc);
    ok = ok && load_symbol(a.lib_handle, "seekdb_value_free", &a.seekdb_value_free);
    ok = ok && load_symbol(a.lib_handle, "seekdb_value_set_null", &a.seekdb_value_set_null);
    ok = ok && load_symbol(a.lib_handle, "seekdb_value_set_int64", &a.seekdb_value_set_int64);
    ok = ok && load_symbol(a.lib_handle, "seekdb_value_set_double", &a.seekdb_value_set_double);
    ok = ok && load_symbol(a.lib_handle, "seekdb_value_set_string", &a.seekdb_value_set_string);
    a.available = ok;
    if (a.lib_handle != nullptr) {
        load_symbol(a.lib_handle, "seekdb_value_set_blob", &a.seekdb_value_set_blob);
        load_symbol(a.lib_handle, "seekdb_row_get_blob_len", &a.seekdb_row_get_blob_len);
        load_symbol(a.lib_handle, "seekdb_row_get_blob", &a.seekdb_row_get_blob);
        load_symbol(a.lib_handle, "seekdb_sqlstate", &a.seekdb_sqlstate);
        load_symbol(a.lib_handle, "seekdb_stmt_reset", &a.seekdb_stmt_reset);
        load_symbol(a.lib_handle, "seekdb_stmt_clear_bindings", &a.seekdb_stmt_clear_bindings);
    }
}

} // namespace

extern "C" JNIEXPORT jint JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeAbiVersion(
        JNIEnv*,
        jclass) {
    // Bump when JNI optional/wired surface changes; keep in sync with engine SEEKDB_ABI_VERSION.
    return 2;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeIsAvailable(
        JNIEnv*,
        jclass) {
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
    int rc = a.seekdb_open(db_path, static_cast<int>(port));
    if (db_dir != nullptr) {
        env->ReleaseStringUTFChars(db_dir, db_path);
    }
    return static_cast<jint>(rc);
}

extern "C" JNIEXPORT void JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeClose(
        JNIEnv*,
        jclass) {
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
    SeekdbDynamicApi::SeekdbConnection conn = nullptr;
    int rc = a.seekdb_connect(&conn, db_name, autocommit == JNI_TRUE);
    if (database != nullptr) {
        env->ReleaseStringUTFChars(database, db_name);
    }
    if (rc != 0 || conn == nullptr) {
        return 0;
    }
    return reinterpret_cast<jlong>(conn);
}

extern "C" JNIEXPORT void JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeDisconnect(
        JNIEnv*,
        jclass,
        jlong connection_ptr) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available) {
        return;
    }
    if (connection_ptr != 0) {
        a.seekdb_disconnect(reinterpret_cast<SeekdbDynamicApi::SeekdbConnection>(connection_ptr));
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
    SeekdbDynamicApi::SeekdbResult result = nullptr;
    int rc = a.seekdb_query_cstr(reinterpret_cast<SeekdbDynamicApi::SeekdbConnection>(connection_ptr), sql_cstr, &result);
    env->ReleaseStringUTFChars(sql, sql_cstr);
    if (rc != 0 || result == nullptr) {
        return 0;
    }
    return reinterpret_cast<jlong>(result);
}

extern "C" JNIEXPORT void JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeResultFree(
        JNIEnv*,
        jclass,
        jlong result_ptr) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available) {
        return;
    }
    if (result_ptr != 0) {
        a.seekdb_result_free(reinterpret_cast<SeekdbDynamicApi::SeekdbResult>(result_ptr));
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeBegin(
        JNIEnv*,
        jclass,
        jlong connection_ptr) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available || connection_ptr == 0) {
        return -1;
    }
    return static_cast<jint>(a.seekdb_begin(
            reinterpret_cast<SeekdbDynamicApi::SeekdbConnection>(connection_ptr)));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeCommit(
        JNIEnv*,
        jclass,
        jlong connection_ptr) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available || connection_ptr == 0) {
        return -1;
    }
    return static_cast<jint>(a.seekdb_commit(
            reinterpret_cast<SeekdbDynamicApi::SeekdbConnection>(connection_ptr)));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeRollback(
        JNIEnv*,
        jclass,
        jlong connection_ptr) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available || connection_ptr == 0) {
        return -1;
    }
    return static_cast<jint>(a.seekdb_rollback(
            reinterpret_cast<SeekdbDynamicApi::SeekdbConnection>(connection_ptr)));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeResultColumnCount(
        JNIEnv*,
        jclass,
        jlong result_ptr) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available || result_ptr == 0) {
        return 0;
    }
    int64_t count = 0;
    int rc = a.seekdb_result_column_count(reinterpret_cast<SeekdbDynamicApi::SeekdbResult>(result_ptr), &count);
    if (rc != 0 || count < 0) {
        return 0;
    }
    return static_cast<jint>(count);
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
            reinterpret_cast<SeekdbDynamicApi::SeekdbResult>(result_ptr),
            static_cast<int32_t>(column_index));
    if (len == static_cast<size_t>(-1)) {
        return nullptr;
    }
    std::string buf(len + 1, '\0');
    int rc = a.seekdb_result_column_name(
            reinterpret_cast<SeekdbDynamicApi::SeekdbResult>(result_ptr),
            static_cast<int32_t>(column_index),
            buf.data(),
            buf.size());
    if (rc != 0) {
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
    auto& a = api();
    if (!a.available || result_ptr == 0 || column_index < 0) {
        return 0;
    }
    int32_t type_id = 0;
    int rc = a.seekdb_result_column_type_id(
            reinterpret_cast<SeekdbDynamicApi::SeekdbResult>(result_ptr),
            static_cast<int64_t>(column_index),
            &type_id);
    if (rc != 0) {
        return 0;
    }
    return static_cast<jint>(type_id);
}

namespace {
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

    int64_t column_count64 = 0;
    int rc = a.seekdb_result_column_count(
            reinterpret_cast<SeekdbDynamicApi::SeekdbResult>(result_ptr),
            &column_count64);
    if (rc != 0 || column_count64 <= 0) {
        return nullptr;
    }
    const jint column_count = static_cast<jint>(column_count64);

    jclass obj_array_cls = env->FindClass("[Ljava/lang/Object;");
    jclass long_cls = env->FindClass("java/lang/Long");
    jclass double_cls = env->FindClass("java/lang/Double");
    jclass bool_cls = env->FindClass("java/lang/Boolean");
    jclass object_cls = env->FindClass("java/lang/Object");
    if (obj_array_cls == nullptr || long_cls == nullptr || double_cls == nullptr || bool_cls == nullptr
            || object_cls == nullptr || env->ExceptionCheck()) {
        return nullptr;
    }
    jmethodID long_value_of = env->GetStaticMethodID(long_cls, "valueOf", "(J)Ljava/lang/Long;");
    jmethodID double_value_of = env->GetStaticMethodID(double_cls, "valueOf", "(D)Ljava/lang/Double;");
    jmethodID bool_value_of = env->GetStaticMethodID(bool_cls, "valueOf", "(Z)Ljava/lang/Boolean;");
    if (long_value_of == nullptr || double_value_of == nullptr || bool_value_of == nullptr
            || env->ExceptionCheck()) {
        return nullptr;
    }

    std::vector<std::vector<jobject>> rows;
    int row_index = 0;
    while (true) {
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
        SeekdbDynamicApi::SeekdbRow row = nullptr;
        int next_rc = a.seekdb_result_row_next(
                reinterpret_cast<SeekdbDynamicApi::SeekdbResult>(result_ptr),
                &row);
        if (next_rc == 100) {
            break;
        }
        if (next_rc != 0 || row == nullptr) {
            break;
        }
        std::vector<jobject> out_row(static_cast<size_t>(column_count), nullptr);
        for (jint i = 0; i < column_count; i++) {
            if (a.seekdb_row_is_null(row, static_cast<int32_t>(i))) {
                continue;
            }
            int32_t type_id = 0;
            a.seekdb_result_column_type_id(
                    reinterpret_cast<SeekdbDynamicApi::SeekdbResult>(result_ptr),
                    static_cast<int64_t>(i),
                    &type_id);
            if (type_id == 0) {
                continue;
            }
            if (type_id == 7) {
                bool v = false;
                if (a.seekdb_row_get_bool(row, static_cast<int32_t>(i), &v) == 0) {
                    out_row[static_cast<size_t>(i)] = env->CallStaticObjectMethod(
                            bool_cls,
                            bool_value_of,
                            static_cast<jboolean>(v ? JNI_TRUE : JNI_FALSE));
                }
            } else if (type_id == 5 || type_id == 6) {
                double v = 0;
                if (a.seekdb_row_get_double(row, static_cast<int32_t>(i), &v) == 0) {
                    out_row[static_cast<size_t>(i)] = env->CallStaticObjectMethod(
                            double_cls, double_value_of, static_cast<jdouble>(v));
                }
            } else if (type_id == 1 || type_id == 2 || type_id == 3 || type_id == 4) {
                int64_t v = 0;
                if (a.seekdb_row_get_int64(row, static_cast<int32_t>(i), &v) == 0) {
                    out_row[static_cast<size_t>(i)] = env->CallStaticObjectMethod(
                            long_cls, long_value_of, static_cast<jlong>(v));
                }
            } else if (type_id == 8 && a.seekdb_row_get_blob_len != nullptr
                    && a.seekdb_row_get_blob != nullptr) {
                size_t blen = a.seekdb_row_get_blob_len(row, static_cast<int32_t>(i));
                if (blen != static_cast<size_t>(-1) && blen > 0) {
                    std::vector<uint8_t> buf(blen);
                    int brc = a.seekdb_row_get_blob(
                            row, static_cast<int32_t>(i), buf.data(), buf.size());
                    if (brc == 0) {
                        jbyteArray jbytes = env->NewByteArray(static_cast<jsize>(blen));
                        if (jbytes != nullptr) {
                            env->SetByteArrayRegion(
                                    jbytes, 0, static_cast<jsize>(blen),
                                    reinterpret_cast<const jbyte*>(buf.data()));
                            out_row[static_cast<size_t>(i)] = jbytes;
                        }
                    }
                }
            } else {
                size_t len = a.seekdb_row_get_string_len(row, static_cast<int32_t>(i));
                std::string value(len + 1, '\0');
                if (a.seekdb_row_get_string(row, static_cast<int32_t>(i), value.data(), value.size())
                        == 0) {
                    out_row[static_cast<size_t>(i)] = env->NewStringUTF(value.c_str());
                }
            }
        }
        rows.emplace_back(std::move(out_row));
        row_index++;
    }

    jobjectArray outer =
            env->NewObjectArray(static_cast<jsize>(rows.size()), obj_array_cls, nullptr);
    if (outer == nullptr || env->ExceptionCheck()) {
        return nullptr;
    }
    for (jsize r = 0; r < static_cast<jsize>(rows.size()); r++) {
        jobjectArray inner = env->NewObjectArray(column_count, object_cls, nullptr);
        if (inner == nullptr || env->ExceptionCheck()) {
            return nullptr;
        }
        for (jint c = 0; c < column_count; c++) {
            jobject value = rows[static_cast<size_t>(r)][static_cast<size_t>(c)];
            if (value != nullptr) {
                env->SetObjectArrayElement(inner, c, value);
                env->DeleteLocalRef(value);
            }
        }
        env->SetObjectArrayElement(outer, r, inner);
        env->DeleteLocalRef(inner);
    }
    return outer;
}

static jobjectArray read_typed_row_cells(
        JNIEnv* env,
        jlong result_ptr,
        SeekdbDynamicApi::SeekdbRow row,
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
        int32_t type_id = 0;
        a.seekdb_result_column_type_id(
                reinterpret_cast<SeekdbDynamicApi::SeekdbResult>(result_ptr),
                static_cast<int64_t>(i),
                &type_id);
        if (type_id == 0) {
            continue;
        }
        jobject cell = nullptr;
        if (type_id == 7) {
            bool v = false;
            if (a.seekdb_row_get_bool(row, static_cast<int32_t>(i), &v) == 0) {
                cell = env->CallStaticObjectMethod(
                        bool_cls,
                        bool_value_of,
                        static_cast<jboolean>(v ? JNI_TRUE : JNI_FALSE));
            }
        } else if (type_id == 5 || type_id == 6) {
            double v = 0;
            if (a.seekdb_row_get_double(row, static_cast<int32_t>(i), &v) == 0) {
                cell = env->CallStaticObjectMethod(double_cls, double_value_of, static_cast<jdouble>(v));
            }
        } else if (type_id == 1 || type_id == 2 || type_id == 3 || type_id == 4) {
            int64_t v = 0;
            if (a.seekdb_row_get_int64(row, static_cast<int32_t>(i), &v) == 0) {
                cell = env->CallStaticObjectMethod(long_cls, long_value_of, static_cast<jlong>(v));
            }
        } else if (type_id == 8 && a.seekdb_row_get_blob_len != nullptr && a.seekdb_row_get_blob != nullptr) {
            size_t blen = a.seekdb_row_get_blob_len(row, static_cast<int32_t>(i));
            if (blen != static_cast<size_t>(-1) && blen > 0) {
                std::vector<uint8_t> buf(blen);
                int brc = a.seekdb_row_get_blob(row, static_cast<int32_t>(i), buf.data(), buf.size());
                if (brc == 0) {
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
            if (a.seekdb_row_get_string(row, static_cast<int32_t>(i), value.data(), value.size()) == 0) {
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
    SeekdbDynamicApi::SeekdbRow row = nullptr;
    int next_rc = a.seekdb_result_row_next(
            reinterpret_cast<SeekdbDynamicApi::SeekdbResult>(result_ptr),
            &row);
    if (next_rc == 100) {
        jclass oc = env->FindClass("java/lang/Object");
        if (oc == nullptr) {
            return nullptr;
        }
        return env->NewObjectArray(0, oc, nullptr);
    }
    if (next_rc != 0 || row == nullptr) {
        return nullptr;
    }
    int64_t column_count64 = 0;
    int rc = a.seekdb_result_column_count(
            reinterpret_cast<SeekdbDynamicApi::SeekdbResult>(result_ptr),
            &column_count64);
    if (rc != 0 || column_count64 <= 0) {
        return nullptr;
    }
    return read_typed_row_cells(
            env, result_ptr, row, static_cast<jint>(column_count64));
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

    int64_t column_count64 = 0;
    int rc = a.seekdb_result_column_count(
            reinterpret_cast<SeekdbDynamicApi::SeekdbResult>(result_ptr),
            &column_count64);
    if (rc != 0 || column_count64 <= 0) {
        return nullptr;
    }
    const jint column_count = static_cast<jint>(column_count64);

    std::vector<std::vector<std::string>> rows;
    std::vector<std::vector<bool>> row_nulls;
    while (true) {
        SeekdbDynamicApi::SeekdbRow row = nullptr;
        int next_rc = a.seekdb_result_row_next(
                reinterpret_cast<SeekdbDynamicApi::SeekdbResult>(result_ptr),
                &row);
        if (next_rc == 100) { // SEEKDB_NO_DATA
            break;
        }
        if (next_rc != 0 || row == nullptr) {
            break;
        }
        std::vector<std::string> out_row(column_count);
        std::vector<bool> out_nulls(column_count, false);
        for (jint i = 0; i < column_count; i++) {
            if (a.seekdb_row_is_null(row, static_cast<int32_t>(i))) {
                out_nulls[i] = true;
                continue;
            }
            size_t len = a.seekdb_row_get_string_len(row, static_cast<int32_t>(i));
            std::string value(len + 1, '\0');
            int get_rc = a.seekdb_row_get_string(row, static_cast<int32_t>(i), value.data(), value.size());
            if (get_rc == 0) {
                out_row[i] = value.c_str();
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
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeResultFetchAllTyped(
        JNIEnv* env,
        jclass,
        jlong result_ptr) {
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
    const char* sql_c = env->GetStringUTFChars(sql, nullptr);
    SeekdbDynamicApi::SeekdbStmt stmt = nullptr;
    int rc = a.seekdb_stmt_prepare(
            reinterpret_cast<SeekdbDynamicApi::SeekdbConnection>(connection_ptr),
            sql_c,
            static_cast<size_t>(strlen(sql_c)),
            &stmt);
    env->ReleaseStringUTFChars(sql, sql_c);
    if (rc != 0 || stmt == nullptr) {
        return 0;
    }
    return reinterpret_cast<jlong>(stmt);
}

extern "C" JNIEXPORT void JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeStmtClose(
        JNIEnv*,
        jclass,
        jlong stmt_ptr) {
    ensure_api_loaded();
    auto& a = api();
    if (a.available && stmt_ptr != 0) {
        a.seekdb_stmt_close(reinterpret_cast<SeekdbDynamicApi::SeekdbStmt>(stmt_ptr));
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeStmtReset(
        JNIEnv*,
        jclass,
        jlong stmt_ptr) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available || stmt_ptr == 0 || a.seekdb_stmt_reset == nullptr) {
        return -1;
    }
    return static_cast<jint>(
            a.seekdb_stmt_reset(reinterpret_cast<SeekdbDynamicApi::SeekdbStmt>(stmt_ptr)));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeStmtClearBindings(
        JNIEnv*,
        jclass,
        jlong stmt_ptr) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available || stmt_ptr == 0 || a.seekdb_stmt_clear_bindings == nullptr) {
        return -1;
    }
    return static_cast<jint>(
            a.seekdb_stmt_clear_bindings(reinterpret_cast<SeekdbDynamicApi::SeekdbStmt>(stmt_ptr)));
}

static jint bind_value_common(
        SeekdbDynamicApi& a,
        SeekdbDynamicApi::SeekdbStmt stmt,
        unsigned int index,
        SeekdbDynamicApi::SeekdbValue value) {
    if (value == nullptr) {
        return -1;
    }
    int rc = a.seekdb_stmt_bind_value(stmt, index, value);
    a.seekdb_value_free(value);
    return static_cast<jint>(rc);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeStmtBindNull(
        JNIEnv*,
        jclass,
        jlong stmt_ptr,
        jint index) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available || stmt_ptr == 0 || index < 0) {
        return -1;
    }
    auto value = a.seekdb_value_alloc();
    a.seekdb_value_set_null(value);
    return bind_value_common(a, reinterpret_cast<SeekdbDynamicApi::SeekdbStmt>(stmt_ptr), static_cast<unsigned int>(index), value);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeStmtBindLong(
        JNIEnv*,
        jclass,
        jlong stmt_ptr,
        jint index,
        jlong v) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available || stmt_ptr == 0 || index < 0) {
        return -1;
    }
    auto value = a.seekdb_value_alloc();
    int rc_set = a.seekdb_value_set_int64(value, static_cast<int64_t>(v));
    if (rc_set != 0) {
        a.seekdb_value_free(value);
        return rc_set;
    }
    return bind_value_common(a, reinterpret_cast<SeekdbDynamicApi::SeekdbStmt>(stmt_ptr), static_cast<unsigned int>(index), value);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeStmtBindDouble(
        JNIEnv*,
        jclass,
        jlong stmt_ptr,
        jint index,
        jdouble v) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available || stmt_ptr == 0 || index < 0) {
        return -1;
    }
    auto value = a.seekdb_value_alloc();
    int rc_set = a.seekdb_value_set_double(value, static_cast<double>(v));
    if (rc_set != 0) {
        a.seekdb_value_free(value);
        return rc_set;
    }
    return bind_value_common(a, reinterpret_cast<SeekdbDynamicApi::SeekdbStmt>(stmt_ptr), static_cast<unsigned int>(index), value);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeStmtBindString(
        JNIEnv* env,
        jclass,
        jlong stmt_ptr,
        jint index,
        jstring value_str) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available || stmt_ptr == 0 || index < 0 || value_str == nullptr) {
        return -1;
    }
    const char* s = env->GetStringUTFChars(value_str, nullptr);
    auto value = a.seekdb_value_alloc();
    int rc_set = a.seekdb_value_set_string(value, s, strlen(s));
    env->ReleaseStringUTFChars(value_str, s);
    if (rc_set != 0) {
        a.seekdb_value_free(value);
        return rc_set;
    }
    return bind_value_common(a, reinterpret_cast<SeekdbDynamicApi::SeekdbStmt>(stmt_ptr), static_cast<unsigned int>(index), value);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeStmtBindBlob(
        JNIEnv* env,
        jclass,
        jlong stmt_ptr,
        jint index,
        jbyteArray value_blob) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available || stmt_ptr == 0 || index < 0 || value_blob == nullptr) {
        return -1;
    }
    jsize len = env->GetArrayLength(value_blob);
    jbyte* data = env->GetByteArrayElements(value_blob, nullptr);
    auto value = a.seekdb_value_alloc();
    int rc_set;
    if (a.seekdb_value_set_blob != nullptr) {
        rc_set = a.seekdb_value_set_blob(
                value, reinterpret_cast<const void*>(data), static_cast<size_t>(len));
    } else {
        rc_set = a.seekdb_value_set_string(
                value, reinterpret_cast<const char*>(data), static_cast<size_t>(len));
    }
    env->ReleaseByteArrayElements(value_blob, data, JNI_ABORT);
    if (rc_set != 0) {
        a.seekdb_value_free(value);
        return rc_set;
    }
    return bind_value_common(a, reinterpret_cast<SeekdbDynamicApi::SeekdbStmt>(stmt_ptr), static_cast<unsigned int>(index), value);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeStmtExecute(
        JNIEnv*,
        jclass,
        jlong stmt_ptr) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available || stmt_ptr == 0) {
        return 0;
    }
    SeekdbDynamicApi::SeekdbResult result = nullptr;
    int rc = a.seekdb_stmt_execute(reinterpret_cast<SeekdbDynamicApi::SeekdbStmt>(stmt_ptr), &result);
    if (rc != 0) {
        return 0;
    }
    return reinterpret_cast<jlong>(result);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeStmtAffectedRows(
        JNIEnv*,
        jclass,
        jlong stmt_ptr) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available || stmt_ptr == 0) {
        return 0;
    }
    return static_cast<jlong>(
            a.seekdb_stmt_affected_rows(reinterpret_cast<SeekdbDynamicApi::SeekdbStmt>(stmt_ptr)));
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeStmtInsertId(
        JNIEnv*,
        jclass,
        jlong stmt_ptr) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available || stmt_ptr == 0) {
        return 0;
    }
    return static_cast<jlong>(
            a.seekdb_stmt_insert_id(reinterpret_cast<SeekdbDynamicApi::SeekdbStmt>(stmt_ptr)));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeLastErrorCode(
        JNIEnv*,
        jclass) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available || a.seekdb_last_error_code == nullptr) {
        return 0;
    }
    return static_cast<jint>(a.seekdb_last_error_code());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeLastErrorMessage(
        JNIEnv* env,
        jclass) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available || a.seekdb_last_error == nullptr) {
        return env->NewStringUTF("");
    }
    const char* msg = a.seekdb_last_error();
    return env->NewStringUTF(msg == nullptr ? "" : msg);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_oceanbase_seekdb_android_core_SeekdbNativeBridge_nativeLastSqlState(
        JNIEnv* env,
        jclass) {
    ensure_api_loaded();
    auto& a = api();
    if (!a.available || a.seekdb_sqlstate == nullptr) {
        return env->NewStringUTF("");
    }
    const char* st = a.seekdb_sqlstate();
    return env->NewStringUTF(st == nullptr ? "" : st);
}
