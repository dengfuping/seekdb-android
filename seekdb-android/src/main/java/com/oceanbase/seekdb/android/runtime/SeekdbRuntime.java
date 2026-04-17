package com.oceanbase.seekdb.android.runtime;

import com.oceanbase.seekdb.android.nativeapi.SeekdbConnection;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Execution/runtime entry points for streaming queries and pooling (full SQLite replacement track).
 */
public final class SeekdbRuntime {
    private static final SeekdbConnectionPool GLOBAL_POOL = new SeekdbConnectionPool();
    /**
     * Process-global reentrant lock for serializing access when only one native connection exists.
     * Optional: call sites may bracket critical sections; Room single-threaded paths may not need it.
     */
    private static final ReentrantLock GLOBAL_EXCLUSIVE = new ReentrantLock();

    private SeekdbRuntime() {}

    public static SeekdbConnectionPool globalPool() {
        return GLOBAL_POOL;
    }

    /** Reentrant lock for {@link SeekdbSerializedSession} and advanced pool coordination. */
    public static ReentrantLock globalExclusiveLock() {
        return GLOBAL_EXCLUSIVE;
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
