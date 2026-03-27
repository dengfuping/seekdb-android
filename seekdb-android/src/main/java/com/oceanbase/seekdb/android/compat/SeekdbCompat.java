package com.oceanbase.seekdb.android.compat;

/**
 * Placeholder entry for the SQLite compatibility layer.
 * Full SupportSQLite implementation is planned in next iteration.
 */
public final class SeekdbCompat {
    public static final String VERSION = "0.1.0-dev";

    private SeekdbCompat() {
    }

    public static SeekdbOpenHelperFactory factory() {
        return new SeekdbOpenHelperFactory();
    }
}
