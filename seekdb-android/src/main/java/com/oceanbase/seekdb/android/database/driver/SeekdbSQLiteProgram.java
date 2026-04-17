package com.oceanbase.seekdb.android.database.driver;

/**
 * sqlite-android / AOSP-shaped program handle (bind + execute). Full parity binds to {@link
 * androidx.sqlite.db.SupportSQLiteStatement} via {@link com.oceanbase.seekdb.android.compat.SeekdbCompatStatement};
 * this type anchors the driver stack for Phase 2 documentation and future direct wiring.
 */
public interface SeekdbSQLiteProgram {
    void close();
}
