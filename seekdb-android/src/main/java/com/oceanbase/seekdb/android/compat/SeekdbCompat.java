package com.oceanbase.seekdb.android.compat;

import com.oceanbase.seekdb.android.nativeapi.SeekdbClient;

/**
 * Entry point for the SQLite compatibility layer (AndroidX {@code SupportSQLite} / Room). Room
 * {@code InvalidationTracker} uses {@code CREATE TEMP TRIGGER} on {@code room_table_modification_log}
 * like sqlite-android; trigger DDL is executed on the embed engine (see {@link SeekdbCompatSql}
 * rewrites).
 */
public final class SeekdbCompat {
    public static final String VERSION = "0.1.0-dev";

    private SeekdbCompat() {
    }

    public static SeekdbOpenHelperFactory factory() {
        return new SeekdbOpenHelperFactory();
    }

    /**
     * Optional full native teardown ({@code seekdb_close()}). Not called from {@link
     * SeekdbOpenHelper#close()} so in-process reopen and per-file {@code seekdb_open} switching
     * (see native {@code do_seekdb_open_inner}) stay stable on Android embed.
     */
    public static void shutdownEmbeddedEngine() {
        SeekdbClient.close();
    }
}
