package com.oceanbase.seekdb.android.core;

import android.os.CancellationSignal;

public final class SeekdbNativeBridge {
    static {
        System.loadLibrary("seekdb_android_jni");
    }

    private SeekdbNativeBridge() {
    }

    public static native int nativeAbiVersion();

    public static native boolean nativeIsAvailable();

    public static native int nativeOpen(String dbDir, int port);

    public static native void nativeClose();

    public static native long nativeConnect(String database, boolean autocommit);

    public static native void nativeDisconnect(long connectionPtr);

    public static native long nativeQuery(long connectionPtr, String sql);

    public static native void nativeResultFree(long resultPtr);

    public static native int nativeBegin(long connectionPtr);

    public static native int nativeCommit(long connectionPtr);

    public static native int nativeRollback(long connectionPtr);

    public static native int nativeResultColumnCount(long resultPtr);

    public static native String nativeResultColumnName(long resultPtr, int columnIndex);

    public static native String[][] nativeResultFetchAll(long resultPtr);

    public static native long nativeStmtPrepare(long connectionPtr, String sql);

    public static native void nativeStmtClose(long stmtPtr);

    /**
     * Optional; returns -1 if {@code seekdb_stmt_reset} is not exported by
     * {@code libseekdb.so}.
     */
    public static native int nativeStmtReset(long stmtPtr);

    /**
     * Optional; returns -1 if {@code seekdb_stmt_clear_bindings} is not exported.
     */
    public static native int nativeStmtClearBindings(long stmtPtr);

    public static native int nativeStmtBindNull(long stmtPtr, int index0Based);

    public static native int nativeStmtBindLong(long stmtPtr, int index0Based, long value);

    public static native int nativeStmtBindDouble(long stmtPtr, int index0Based, double value);

    public static native int nativeStmtBindString(long stmtPtr, int index0Based, String value);

    public static native int nativeStmtBindBlob(long stmtPtr, int index0Based, byte[] value);

    public static native long nativeStmtExecute(long stmtPtr);

    /** Last {@code seekdb_stmt_execute} (or bind_param) return code for this statement; 0 if success. */
    public static native int nativeStmtLastExecuteRc(long stmtPtr);

    public static native long nativeStmtAffectedRows(long stmtPtr);

    public static native long nativeStmtInsertId(long stmtPtr);

    public static native int nativeLastErrorCode();

    /**
     * Connection-scoped engine errno from {@code seekdb_errno(SeekdbHandle)} when exported by
     * {@code libseekdb.so}; otherwise 0. Values are cast to signed 32-bit (e.g. OB errors).
     */
    public static native int nativeErrno(long connectionPtr);

    public static native String nativeLastErrorMessage();

    /**
     * Thread-local SQLSTATE is not available without a connection; prefer
     * {@link #nativeLastSqlState(long)}.
     */
    public static native String nativeLastSqlState();

    /** SQLSTATE from {@code seekdb_sqlstate(SeekdbHandle)} (official C ABI). */
    public static native String nativeLastSqlState(long connectionPtr);

    public static native int nativeResultColumnTypeId(long resultPtr, int columnIndex);

    public static native Object[][] nativeResultFetchAllTyped(long resultPtr);

    public static native Object[][] nativeResultFetchAllTyped(long resultPtr, CancellationSignal cancellationSignal);

    /**
     * Advances the result cursor and returns one row as typed {@link Object} cells.
     * Returns a
     * zero-length array at end-of-data (SEEKDB_NO_DATA / rc 100). Returns
     * {@code null} on native error;
     * callers should check {@link #nativeLastErrorCode()}.
     */
    public static native Object[] nativeResultReadNextRowTyped(long resultPtr);

    /**
     * Process-wide open {@link com.oceanbase.seekdb.android.compat.SeekdbCompatDatabase} handles for
     * App Inspection (stored as JNI global refs so Database Inspector sees the same instances even
     * when multiple ClassLoaders would duplicate Java static state, or when {@code findInstances} is
     * empty on newer Android releases).
     */
    public static native void nativeInspectionRegisterOpenDatabase(Object database);

    public static native void nativeInspectionUnregisterOpenDatabase(Object database);

    /** Returns a new array of local references; may be empty, never {@code null}. */
    public static native Object[] nativeInspectionSnapshotOpenDatabases();
}
