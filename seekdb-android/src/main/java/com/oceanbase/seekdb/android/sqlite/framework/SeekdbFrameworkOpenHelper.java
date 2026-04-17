package com.oceanbase.seekdb.android.sqlite.framework;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import com.oceanbase.seekdb.android.compat.SeekdbCompat;

/**
 * Android {@code SQLiteOpenHelper}-shaped entry over SeekDB: delegates to {@link SeekdbCompat#factory()}
 * and exposes {@link SupportSQLiteDatabase} (Room / SupportSQLite contract). Prefer this when migrating
 * from hand-rolled {@code SQLiteOpenHelper} toward SeekDB while staying on SupportSQLite types.
 */
public final class SeekdbFrameworkOpenHelper implements SupportSQLiteOpenHelper {
    private final SupportSQLiteOpenHelper delegate;

    public SeekdbFrameworkOpenHelper(Context context, @Nullable String name, SupportSQLiteOpenHelper.Callback callback) {
        SupportSQLiteOpenHelper.Configuration configuration =
                SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build();
        this.delegate = SeekdbCompat.factory().create(configuration);
    }

    @Override
    @Nullable
    public String getDatabaseName() {
        return delegate.getDatabaseName();
    }

    @Override
    public void setWriteAheadLoggingEnabled(boolean enabled) {
        delegate.setWriteAheadLoggingEnabled(enabled);
    }

    @Override
    public SupportSQLiteDatabase getWritableDatabase() {
        return delegate.getWritableDatabase();
    }

    @Override
    public SupportSQLiteDatabase getReadableDatabase() {
        return delegate.getReadableDatabase();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
