package com.oceanbase.seekdb.android.runtime;

import com.oceanbase.seekdb.android.nativeapi.SeekdbConnection;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-scoped view over a single shared {@link SeekdbConnection}.
 *
 * <p><b>Concurrency model (single shared connection):</b> one logical database handle is
 * backed by one native connection opened by {@link com.oceanbase.seekdb.android.compat.SeekdbOpenHelper}.
 * The same {@link SeekdbConnection} must not be used concurrently from multiple threads; callers should
 * follow Room/SupportSQLite expectations (single-threaded access or app-level serialization). Use {@link
 * SeekdbConnectionPool} when multiple logical holders need acquire/release symmetry; this class remains
 * the low-level thread-local cache for the primary connection.
 */
public final class SeekdbSessionManager {
    private final AtomicReference<SeekdbConnection> sharedConnection = new AtomicReference<>();
    private final ThreadLocal<SeekdbConnection> threadBoundConnection = new ThreadLocal<>();

    public void setSharedConnection(SeekdbConnection connection) {
        sharedConnection.set(connection);
    }

    public SeekdbConnection currentConnection() {
        SeekdbConnection threadConn = threadBoundConnection.get();
        if (threadConn != null && !threadConn.isClosed()) {
            return threadConn;
        }
        SeekdbConnection shared = sharedConnection.get();
        if (shared != null && !shared.isClosed()) {
            threadBoundConnection.set(shared);
            return shared;
        }
        return null;
    }

    public void clearThreadSession() {
        threadBoundConnection.remove();
    }
}
