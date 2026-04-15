package com.oceanbase.seekdb.android.database;

import android.database.AbstractWindowedCursor;
import android.database.CursorWindow;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import com.oceanbase.seekdb.android.core.SeekdbNativeBridge;
import java.util.ArrayList;

/**
 * {@link AbstractWindowedCursor} over a SeekDB statement result: rows are read lazily via {@link
 * SeekdbNativeBridge#nativeResultReadNextRowTyped} and copied into a {@link CursorWindow} in chunks
 * (similar to {@link android.database.sqlite.SQLiteCursor}). A row buffer grows as the scan advances,
 * so random access to rows not yet fetched forces a forward read from the native result.
 *
 * <p>Owns the native statement and result until {@link #close()}.
 */
public final class SeekdbWindowedCursor extends AbstractWindowedCursor {
    private static final int NO_COUNT = -1;
    private static final int CANCEL_POLL_INTERVAL = 32;
    /** Upper bound on rows per window fill; actual count may be lower if {@link CursorWindow#allocRow} fails. */
    private static final int MAX_ROWS_PER_FILL = 4096;

    private long stmtPtr;
    private long resultPtr;
    private final String[] columnNames;
    private final CancellationSignal cancellationSignal;
    private final ArrayList<Object[]> buffer = new ArrayList<>();
    private boolean eof;
    private int mCount = NO_COUNT;
    private int mWindowRowCapacity;
    private int rowsFetchedSincePoll;
    private boolean nativeReleased;

    public SeekdbWindowedCursor(
            long stmtPtr, long resultPtr, String[] columnNames, CancellationSignal cancellationSignal) {
        this.stmtPtr = stmtPtr;
        this.resultPtr = resultPtr;
        this.columnNames = columnNames != null ? columnNames : new String[0];
        this.cancellationSignal = cancellationSignal;
    }

    private void throwIfCanceled() {
        if (cancellationSignal != null && cancellationSignal.isCanceled()) {
            throw new OperationCanceledException();
        }
    }

    private void pollCancelIfNeeded() {
        if (++rowsFetchedSincePoll >= CANCEL_POLL_INTERVAL) {
            rowsFetchedSincePoll = 0;
            throwIfCanceled();
        }
    }

    /** Ensures {@code buffer.size() > lastIndexInclusive} or EOF (whichever comes first). */
    private void ensureBufferedThrough(int lastIndexInclusive) {
        while (buffer.size() <= lastIndexInclusive && !eof) {
            pollCancelIfNeeded();
            Object[] row = SeekdbNativeBridge.nativeResultReadNextRowTyped(resultPtr);
            if (row == null) {
                throw new IllegalStateException(
                        "nativeResultReadNextRowTyped failed: rc="
                                + SeekdbNativeBridge.nativeLastErrorCode()
                                + " msg="
                                + SeekdbNativeBridge.nativeLastErrorMessage());
            }
            if (row.length == 0) {
                eof = true;
                if (mCount == NO_COUNT) {
                    mCount = buffer.size();
                }
                break;
            }
            buffer.add(row);
        }
    }

    private void drainToEof() {
        while (!eof) {
            pollCancelIfNeeded();
            Object[] row = SeekdbNativeBridge.nativeResultReadNextRowTyped(resultPtr);
            if (row == null) {
                throw new IllegalStateException(
                        "nativeResultReadNextRowTyped failed: rc="
                                + SeekdbNativeBridge.nativeLastErrorCode());
            }
            if (row.length == 0) {
                eof = true;
                break;
            }
            buffer.add(row);
        }
        if (mCount == NO_COUNT) {
            mCount = buffer.size();
        }
    }

    private static int pickFillStart(int requiredPos, int windowRowCapacity) {
        if (windowRowCapacity <= 0) {
            return Math.max(requiredPos - 64, 0);
        }
        return Math.max(requiredPos - windowRowCapacity / 3, 0);
    }

    private void fillWindowInternal(int requiredPos) {
        throwIfCanceled();
        if (requiredPos < 0) {
            throw new IllegalArgumentException("requiredPos cannot be negative");
        }
        if (mWindow != null) {
            mWindow.clear();
        } else {
            setWindow(new CursorWindow("seekdb"));
        }
        try {
            int colCount = columnNames.length;
            if (colCount > 0 && !mWindow.setNumColumns(colCount)) {
                throw new IllegalStateException("CursorWindow.setNumColumns failed");
            }

            ensureBufferedThrough(requiredPos);
            if (buffer.isEmpty() || requiredPos >= buffer.size()) {
                mWindow.setStartPosition(Math.max(requiredPos, 0));
                return;
            }

            int startPos = pickFillStart(requiredPos, mWindowRowCapacity);
            ensureBufferedThrough(startPos + MAX_ROWS_PER_FILL - 1);

            int endExclusive = Math.min(startPos + MAX_ROWS_PER_FILL, buffer.size());
            if (startPos >= endExclusive) {
                startPos = Math.max(0, Math.min(requiredPos, buffer.size() - 1));
                endExclusive = buffer.size();
            }

            mWindow.setStartPosition(startPos);
            for (int absRow = startPos; absRow < endExclusive; absRow++) {
                if (!mWindow.allocRow()) {
                    break;
                }
                SeekdbCursorWindowUtil.putRow(mWindow, absRow, buffer.get(absRow));
            }
            int n = mWindow.getNumRows();
            if (n > mWindowRowCapacity) {
                mWindowRowCapacity = n;
            }
            if (eof && mCount == NO_COUNT) {
                mCount = buffer.size();
            }
        } catch (RuntimeException ex) {
            if (mWindow != null) {
                mWindow.close();
                mWindow = null;
            }
            throw ex;
        }
    }

    @Override
    public boolean onMove(int oldPosition, int newPosition) {
        if (mWindow == null
                || newPosition < mWindow.getStartPosition()
                || newPosition >= mWindow.getStartPosition() + mWindow.getNumRows()) {
            fillWindowInternal(newPosition);
        }
        return true;
    }

    @Override
    public int getCount() {
        if (mCount == NO_COUNT) {
            drainToEof();
        }
        return mCount;
    }

    @Override
    public String[] getColumnNames() {
        return columnNames;
    }

    @Override
    public void close() {
        if (isClosed()) {
            return;
        }
        super.close();
        releaseNative();
    }

    private void releaseNative() {
        if (nativeReleased) {
            return;
        }
        nativeReleased = true;
        // resultPtr refers to stmt's result_set; freed when the statement is closed.
        resultPtr = 0L;
        if (stmtPtr != 0L) {
            SeekdbNativeBridge.nativeStmtClose(stmtPtr);
            stmtPtr = 0L;
        }
    }
}
