package com.oceanbase.seekdb.android.nativeapi;

import com.oceanbase.seekdb.android.core.SeekdbNativeBridge;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SeekdbResultSet implements AutoCloseable {
    private final long resultPtr;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    SeekdbResultSet(long resultPtr) {
        this.resultPtr = resultPtr;
    }

    public long pointer() {
        return resultPtr;
    }

    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true) && resultPtr != 0L) {
            SeekdbNativeBridge.nativeResultFree(resultPtr);
        }
    }
}
