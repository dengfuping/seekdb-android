package com.oceanbase.seekdb.android.database.driver;

import com.oceanbase.seekdb.android.database.SeekdbWindowedCursor;
/**
 * Default {@link SeekdbSQLiteCursorDriver}: wraps statement/result pointers into {@link SeekdbWindowedCursor}.
 */
public final class SeekdbDefaultCursorDriver implements SeekdbSQLiteCursorDriver {
    private final String[] columnNames;

    public SeekdbDefaultCursorDriver(String[] columnNames) {
        this.columnNames = columnNames != null ? columnNames : new String[0];
    }

    @Override
    public String[] columnNames() {
        return columnNames;
    }

    @Override
    public SeekdbWindowedCursor query(long stmtPtr, long resultPtr, android.os.CancellationSignal cancellationSignal) {
        return new SeekdbWindowedCursor(stmtPtr, resultPtr, columnNames, cancellationSignal);
    }
}
