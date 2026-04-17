package com.oceanbase.seekdb.android.database.driver;

import com.oceanbase.seekdb.android.database.SeekdbWindowedCursor;

/**
 * sqlite-android {@code SQLiteCursorDriver}-shaped hook: produces a {@link Cursor} over a prepared
 * statement. Default implementation path uses {@link SeekdbWindowedCursor} + {@link
 * com.oceanbase.seekdb.android.runtime.SeekdbStreamingPolicy}.
 */
public interface SeekdbSQLiteCursorDriver {
    /** Returns column names for the current query (after prepare). */
    String[] columnNames();

    /**
     * Executes the query and returns a windowed cursor; callers must {@link Cursor#close()}.
     */
    SeekdbWindowedCursor query(long stmtPtr, long resultPtr, android.os.CancellationSignal cancellationSignal);
}
