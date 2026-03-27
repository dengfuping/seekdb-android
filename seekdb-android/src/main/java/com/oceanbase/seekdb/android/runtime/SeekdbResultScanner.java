package com.oceanbase.seekdb.android.runtime;

import com.oceanbase.seekdb.android.core.SeekdbNativeBridge;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Incremental row iterator over a native {@code SeekdbResult} without building a full {@code Object[][]}
 * on the Java heap. The caller must {@link #close()} to release the native result.
 *
 * <p>Typical usage: after {@code seekdb_stmt_execute} (or equivalent) yields a non-zero result pointer,
 * construct a scanner, consume rows, then close. Not thread-safe.
 */
public final class SeekdbResultScanner implements Iterator<Object[]>, AutoCloseable {
    private final long resultPtr;
    private boolean closed;
    private Object[] prefetched;
    private boolean eof;

    public SeekdbResultScanner(long resultPtr) {
        if (resultPtr == 0L) {
            throw new IllegalArgumentException("resultPtr");
        }
        this.resultPtr = resultPtr;
    }

    @Override
    public boolean hasNext() {
        if (closed) {
            return false;
        }
        if (eof) {
            return false;
        }
        if (prefetched != null) {
            return true;
        }
        prefetched = SeekdbNativeBridge.nativeResultReadNextRowTyped(resultPtr);
        if (prefetched == null) {
            throw new IllegalStateException(
                    "nativeResultReadNextRowTyped failed: rc="
                            + SeekdbNativeBridge.nativeLastErrorCode()
                            + " msg="
                            + SeekdbNativeBridge.nativeLastErrorMessage());
        }
        if (prefetched.length == 0) {
            prefetched = null;
            eof = true;
            return false;
        }
        return true;
    }

    @Override
    public Object[] next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        Object[] row = prefetched;
        prefetched = null;
        return row;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        prefetched = null;
        SeekdbNativeBridge.nativeResultFree(resultPtr);
    }
}
