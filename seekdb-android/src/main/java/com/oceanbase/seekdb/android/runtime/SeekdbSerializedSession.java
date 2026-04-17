package com.oceanbase.seekdb.android.runtime;

import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;

/**
 * SQLiteSession-like helper: runs work while holding {@link SeekdbRuntime#globalExclusiveLock()} so
 * that multiple API holders sharing one native connection do not interleave operations (single-connection
 * mode). When SeekDB exposes multiple connections, this may narrow to writer-only serialization.
 */
public final class SeekdbSerializedSession {
    private SeekdbSerializedSession() {}

    /**
     * Runs {@code work} exclusively with respect to other callers using the same global lock
     * (reentrant on the same thread).
     */
    public static <T> T runExclusive(Callable<T> work) throws Exception {
        ReentrantLock lock = SeekdbRuntime.globalExclusiveLock();
        lock.lock();
        try {
            return work.call();
        } finally {
            lock.unlock();
        }
    }

    /** Void variant of {@link #runExclusive(Callable)}. */
    public static void runExclusive(Runnable work) {
        ReentrantLock lock = SeekdbRuntime.globalExclusiveLock();
        lock.lock();
        try {
            work.run();
        } finally {
            lock.unlock();
        }
    }
}
