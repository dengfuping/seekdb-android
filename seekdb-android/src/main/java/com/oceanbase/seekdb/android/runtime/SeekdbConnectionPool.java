package com.oceanbase.seekdb.android.runtime;

import com.oceanbase.seekdb.android.nativeapi.SeekdbConnection;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Minimal connection pool placeholder modeled after Android {@code SQLiteConnectionPool}-style usage.
 *
 * <p>Current implementation: a single primary connection with symmetric {@link #acquire()} / {@link
 * #release(PooledConnection)} for API shape only. When SeekDB exposes multiple connections (readers /
 * writer), extend this class without changing the public acquire/release contract.
 */
public final class SeekdbConnectionPool {
    private final AtomicReference<SeekdbConnection> primary = new AtomicReference<>();
    private final Object gate = new Object();

    public void installPrimary(SeekdbConnection connection) {
        primary.set(connection);
    }

    public SeekdbConnection primaryForTests() {
        return primary.get();
    }

    /**
     * @param writer ignored until multi-connection ABI exists; reserved for future reader/writer split
     */
    public PooledConnection acquire(boolean writer) {
        synchronized (gate) {
            SeekdbConnection c = primary.get();
            if (c == null || c.isClosed()) {
                throw new IllegalStateException("No primary connection installed");
            }
            return new PooledConnection(c);
        }
    }

    public void release(PooledConnection handle) {
        if (handle == null) {
            return;
        }
        // Single-connection mode: no-op
    }

    public static final class PooledConnection {
        private final SeekdbConnection connection;

        PooledConnection(SeekdbConnection connection) {
            this.connection = connection;
        }

        public SeekdbConnection connection() {
            return connection;
        }
    }
}
