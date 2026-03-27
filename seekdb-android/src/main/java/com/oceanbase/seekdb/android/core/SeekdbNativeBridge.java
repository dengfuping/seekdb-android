package com.oceanbase.seekdb.android.core;

import android.os.CancellationSignal;

public final class SeekdbNativeBridge {
    static {
        System.loadLibrary("seekdb_android_jni");
    }

    private SeekdbNativeBridge() {}

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

    /** Optional; returns -1 if {@code seekdb_stmt_reset} is not exported by {@code libseekdb.so}. */
    public static native int nativeStmtReset(long stmtPtr);

    /** Optional; returns -1 if {@code seekdb_stmt_clear_bindings} is not exported. */
    public static native int nativeStmtClearBindings(long stmtPtr);

    public static native int nativeStmtBindNull(long stmtPtr, int index0Based);

    public static native int nativeStmtBindLong(long stmtPtr, int index0Based, long value);

    public static native int nativeStmtBindDouble(long stmtPtr, int index0Based, double value);

    public static native int nativeStmtBindString(long stmtPtr, int index0Based, String value);

    public static native int nativeStmtBindBlob(long stmtPtr, int index0Based, byte[] value);

    public static native long nativeStmtExecute(long stmtPtr);

    public static native long nativeStmtAffectedRows(long stmtPtr);

    public static native long nativeStmtInsertId(long stmtPtr);

    public static native int nativeLastErrorCode();

    public static native String nativeLastErrorMessage();

    public static native String nativeLastSqlState();

    public static native int nativeResultColumnTypeId(long resultPtr, int columnIndex);

    public static native Object[][] nativeResultFetchAllTyped(long resultPtr);

    public static native Object[][] nativeResultFetchAllTyped(long resultPtr, CancellationSignal cancellationSignal);

    /**
     * Advances the result cursor and returns one row as typed {@link Object} cells. Returns a
     * zero-length array at end-of-data (SEEKDB_NO_DATA / rc 100). Returns {@code null} on native error;
     * callers should check {@link #nativeLastErrorCode()}.
     */
    public static native Object[] nativeResultReadNextRowTyped(long resultPtr);
}
