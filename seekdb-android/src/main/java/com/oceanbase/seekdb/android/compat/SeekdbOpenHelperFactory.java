package com.oceanbase.seekdb.android.compat;

import androidx.sqlite.db.SupportSQLiteOpenHelper;

/**
 * Room integration entry point.
 * Current implementation provides wiring skeleton and will be completed with
 * SupportSQLiteDatabase implementation in next phase.
 */
public final class SeekdbOpenHelperFactory implements SupportSQLiteOpenHelper.Factory {
    @Override
    public SupportSQLiteOpenHelper create(SupportSQLiteOpenHelper.Configuration configuration) {
        return new SeekdbOpenHelper(configuration);
    }
}
