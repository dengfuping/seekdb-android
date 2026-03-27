package com.oceanbase.seekdb.android.compat;

import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import com.oceanbase.seekdb.android.nativeapi.SeekdbClient;
import com.oceanbase.seekdb.android.nativeapi.SeekdbConnection;
import com.oceanbase.seekdb.android.runtime.SeekdbRuntime;

final class SeekdbOpenHelper implements SupportSQLiteOpenHelper {
    private final SupportSQLiteOpenHelper.Configuration configuration;
    private volatile boolean writeAheadLoggingEnabled;
    private final SeekdbCompatDatabase database;
    private boolean initialized;
    private SeekdbConnection connection;

    SeekdbOpenHelper(SupportSQLiteOpenHelper.Configuration configuration) {
        this.configuration = configuration;
        this.database = new SeekdbCompatDatabase(configuration.name);
    }

    @Override
    public String getDatabaseName() {
        return configuration.name;
    }

    @Override
    public void setWriteAheadLoggingEnabled(boolean enabled) {
        this.writeAheadLoggingEnabled = enabled;
        if (initialized) {
            if (enabled) {
                database.enableWriteAheadLogging();
            } else {
                database.disableWriteAheadLogging();
            }
        }
    }

    boolean isWriteAheadLoggingEnabled() {
        return writeAheadLoggingEnabled;
    }

    @Override
    public SupportSQLiteDatabase getWritableDatabase() {
        initializeIfNeeded();
        return database;
    }

    @Override
    public SupportSQLiteDatabase getReadableDatabase() {
        initializeIfNeeded();
        return database;
    }

    private synchronized void initializeIfNeeded() {
        if (initialized) {
            return;
        }
        if (!SeekdbClient.isNativeAvailable()) {
            throw new IllegalStateException("SeekDB native library is not available");
        }
        initialized = true;
        String dbName = configuration.name == null ? "seekdb_android.db" : configuration.name;
        String dbPath = configuration.context.getDatabasePath(dbName).getParent();
        SeekdbClient.open(dbPath, 0);
        connection = SeekdbClient.connect(dbName, true);
        database.setConnection(connection);
        SeekdbRuntime.installGlobalPrimary(connection);
        if (writeAheadLoggingEnabled) {
            database.enableWriteAheadLogging();
        }

        configuration.callback.onConfigure(database);
        if (database.getVersion() == 0) {
            configuration.callback.onCreate(database);
            database.setVersion(configuration.callback.version);
        } else if (database.getVersion() < configuration.callback.version) {
            configuration.callback.onUpgrade(database, database.getVersion(), configuration.callback.version);
            database.setVersion(configuration.callback.version);
        } else if (database.getVersion() > configuration.callback.version) {
            configuration.callback.onDowngrade(database, database.getVersion(), configuration.callback.version);
            database.setVersion(configuration.callback.version);
        }
        configuration.callback.onOpen(database);
    }

    @Override
    public void close() {
        database.close();
        SeekdbClient.close();
    }
}
