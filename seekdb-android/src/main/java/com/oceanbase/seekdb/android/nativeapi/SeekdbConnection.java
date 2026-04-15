package com.oceanbase.seekdb.android.nativeapi;

import com.oceanbase.seekdb.android.core.SeekdbNativeBridge;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SeekdbConnection implements AutoCloseable {
    private final long connectionPtr;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    /** Serializes all JNI on this connection (Room may query from a worker while the app uses main). */
    private final Object nativeMutex = new Object();

    SeekdbConnection(long connectionPtr) {
        this.connectionPtr = connectionPtr;
    }

    public long pointer() {
        return connectionPtr;
    }

    public Object nativeMutex() {
        return nativeMutex;
    }

    public SeekdbResultSet query(String sql) {
        if (isClosed()) {
            throw new IllegalStateException("Connection already closed");
        }
        synchronized (nativeMutex) {
            long resultPtr = SeekdbNativeBridge.nativeQuery(connectionPtr, sql);
            if (resultPtr == 0L) {
                throw new IllegalStateException("seekdb_query returned null result");
            }
            return new SeekdbResultSet(resultPtr);
        }
    }

    public boolean isClosed() {
        return closed.get();
    }

    public int begin() {
        synchronized (nativeMutex) {
            return SeekdbNativeBridge.nativeBegin(connectionPtr);
        }
    }

    public int commit() {
        synchronized (nativeMutex) {
            return SeekdbNativeBridge.nativeCommit(connectionPtr);
        }
    }

    public int rollback() {
        synchronized (nativeMutex) {
            return SeekdbNativeBridge.nativeRollback(connectionPtr);
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            synchronized (nativeMutex) {
                SeekdbNativeBridge.nativeDisconnect(connectionPtr);
            }
        }
    }
}
