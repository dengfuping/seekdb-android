package com.oceanbase.seekdb.android.nativeapi;

import com.oceanbase.seekdb.android.core.SeekdbNativeBridge;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SeekdbConnection implements AutoCloseable {
    private final long connectionPtr;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    SeekdbConnection(long connectionPtr) {
        this.connectionPtr = connectionPtr;
    }

    public long pointer() {
        return connectionPtr;
    }

    public SeekdbResultSet query(String sql) {
        if (isClosed()) {
            throw new IllegalStateException("Connection already closed");
        }
        long resultPtr = SeekdbNativeBridge.nativeQuery(connectionPtr, sql);
        if (resultPtr == 0L) {
            throw new IllegalStateException("seekdb_query returned null result");
        }
        return new SeekdbResultSet(resultPtr);
    }

    public boolean isClosed() {
        return closed.get();
    }

    public int begin() {
        return SeekdbNativeBridge.nativeBegin(connectionPtr);
    }

    public int commit() {
        return SeekdbNativeBridge.nativeCommit(connectionPtr);
    }

    public int rollback() {
        return SeekdbNativeBridge.nativeRollback(connectionPtr);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            SeekdbNativeBridge.nativeDisconnect(connectionPtr);
        }
    }
}
