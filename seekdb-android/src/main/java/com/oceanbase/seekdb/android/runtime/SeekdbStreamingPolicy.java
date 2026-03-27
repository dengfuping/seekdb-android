package com.oceanbase.seekdb.android.runtime;

/**
 * Controls whether {@link com.oceanbase.seekdb.android.compat.SeekdbCompatStatement} returns a
 * {@link android.database.AbstractWindowedCursor} ({@link
 * com.oceanbase.seekdb.android.database.SeekdbWindowedCursor}) instead of materializing the full result
 * into a {@link android.database.MatrixCursor}.
 *
 * <p>Default is {@code false} to maximize Room/SupportSQLite compatibility. Set {@code true} for large
 * result sets when callers may stop iterating early (saves peak heap vs full {@code Object[][]}).
 */
public final class SeekdbStreamingPolicy {
    private static volatile boolean useStreamingQueryCursors;

    private SeekdbStreamingPolicy() {}

    public static boolean useStreamingQueryCursors() {
        return useStreamingQueryCursors;
    }

    public static void setUseStreamingQueryCursors(boolean enabled) {
        useStreamingQueryCursors = enabled;
    }
}
