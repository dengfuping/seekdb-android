package com.oceanbase.seekdb.android.sqlite;

import androidx.sqlite.db.SupportSQLiteOpenHelper;
import com.oceanbase.seekdb.android.compat.SeekdbCompat;
import com.oceanbase.seekdb.android.core.SeekdbNativeBridge;
import com.oceanbase.seekdb.android.runtime.SeekdbStreamingPolicy;

/**
 * Public entry for Room / {@link androidx.sqlite.db.SupportSQLite} integration and JNI ABI introspection.
 * Broader {@code SQLiteDatabase}-shaped APIs are on the roadmap; see project docs under {@code
 * docs/seekdb-android/}.
 */
public final class SeekdbSQLite {
    private SeekdbSQLite() {}

    /** Same as {@link SeekdbCompat#factory()}; use with {@code Room.databaseBuilder(...).openHelperFactory(...)}. */
    public static SupportSQLiteOpenHelper.Factory supportOpenHelperFactory() {
        return SeekdbCompat.factory();
    }

    /** JNI bridge ABI version; increments when optional native symbols or semantics change. */
    public static int jniAbiVersion() {
        return SeekdbNativeBridge.nativeAbiVersion();
    }

    public static boolean isNativeLibraryAvailable() {
        return SeekdbNativeBridge.nativeIsAvailable();
    }

    /**
     * When {@code true}, {@code SupportSQLite} query cursors use {@link
     * com.oceanbase.seekdb.android.database.SeekdbWindowedCursor} ({@link
     * android.database.AbstractWindowedCursor}) instead of full in-memory materialization. Default
     * {@code false} for Room compatibility.
     */
    public static void setStreamingQueryCursorsEnabled(boolean enabled) {
        SeekdbStreamingPolicy.setUseStreamingQueryCursors(enabled);
    }

    public static boolean isStreamingQueryCursorsEnabled() {
        return SeekdbStreamingPolicy.useStreamingQueryCursors();
    }
}
