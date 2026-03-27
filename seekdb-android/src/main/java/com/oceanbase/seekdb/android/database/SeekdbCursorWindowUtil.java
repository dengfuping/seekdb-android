package com.oceanbase.seekdb.android.database;

import android.database.CursorWindow;
import com.oceanbase.seekdb.android.runtime.SeekdbResultScanner;

/**
 * Helpers to copy SeekDB-typed row cells (as returned by {@link
 * com.oceanbase.seekdb.android.core.SeekdbNativeBridge#nativeResultReadNextRowTyped}) into a {@link
 * CursorWindow}, for windowed cursors ({@link android.database.AbstractWindowedCursor}) and optional IPC.
 */
public final class SeekdbCursorWindowUtil {
    private SeekdbCursorWindowUtil() {}

    public static void putRow(CursorWindow window, int row, Object[] cells) {
        if (window == null || cells == null) {
            return;
        }
        for (int i = 0; i < cells.length; i++) {
            Object v = cells[i];
            if (v == null) {
                window.putNull(row, i);
            } else if (v instanceof byte[]) {
                byte[] b = (byte[]) v;
                window.putBlob(b, row, i);
            } else if (v instanceof Long) {
                window.putLong(((Long) v).longValue(), row, i);
            } else if (v instanceof Integer) {
                window.putLong(((Integer) v).longValue(), row, i);
            } else if (v instanceof Short) {
                window.putLong(((Short) v).longValue(), row, i);
            } else if (v instanceof Byte) {
                window.putLong(((Byte) v).longValue(), row, i);
            } else if (v instanceof Double) {
                window.putDouble(((Double) v).doubleValue(), row, i);
            } else if (v instanceof Float) {
                window.putDouble(((Float) v).doubleValue(), row, i);
            } else if (v instanceof Boolean) {
                window.putLong(((Boolean) v) ? 1L : 0L, row, i);
            } else {
                window.putString(String.valueOf(v), row, i);
            }
        }
    }

    /** Copies up to {@code maxRows} from {@code scanner} into {@code window} at row indices {@code 0 ..}. */
    public static int fillChunk(CursorWindow window, SeekdbResultScanner scanner, int maxRows) {
        if (window == null || scanner == null || maxRows <= 0) {
            return 0;
        }
        int r = 0;
        while (r < maxRows && scanner.hasNext()) {
            putRow(window, r, scanner.next());
            r++;
        }
        return r;
    }
}
