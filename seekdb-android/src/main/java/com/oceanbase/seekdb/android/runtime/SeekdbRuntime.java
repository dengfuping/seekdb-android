package com.oceanbase.seekdb.android.runtime;

import com.oceanbase.seekdb.android.nativeapi.SeekdbConnection;

/**
 * Execution/runtime entry points for streaming queries and pooling (full SQLite replacement track).
 */
public final class SeekdbRuntime {
    private static final SeekdbConnectionPool GLOBAL_POOL = new SeekdbConnectionPool();

    private SeekdbRuntime() {}

    public static SeekdbConnectionPool globalPool() {
        return GLOBAL_POOL;
    }

    /** Install the single shared connection used by {@link #globalPool()}. */
    public static void installGlobalPrimary(SeekdbConnection connection) {
        GLOBAL_POOL.installPrimary(connection);
    }

    /**
     * Opens a scanner over an existing native result pointer. Ownership transfers to the scanner until
     * {@link SeekdbResultScanner#close()}.
     */
    public static SeekdbResultScanner openResultScanner(long resultPtr) {
        return new SeekdbResultScanner(resultPtr);
    }
}
