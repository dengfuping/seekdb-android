package com.oceanbase.seekdb.android.compat;

import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import com.oceanbase.seekdb.android.core.SeekdbNativeBridge;
import com.oceanbase.seekdb.android.nativeapi.SeekdbClient;
import com.oceanbase.seekdb.android.nativeapi.SeekdbConnection;
import com.oceanbase.seekdb.android.runtime.SeekdbRuntime;

final class SeekdbOpenHelper implements SupportSQLiteOpenHelper {
    private static final String SCHEMA_VERSION_PREFS = "seekdb_compat_schema_version";

    private final SupportSQLiteOpenHelper.Configuration configuration;
    private volatile boolean writeAheadLoggingEnabled;
    private final SeekdbCompatDatabase database;
    private boolean initialized;
    /** Prevents concurrent init; paired with {@link #initializingThread} for same-thread reentrancy. */
    private boolean initializing;
    private Thread initializingThread;
    private SeekdbConnection connection;

    SeekdbOpenHelper(SupportSQLiteOpenHelper.Configuration configuration) {
        this.configuration = configuration;
        this.database = new SeekdbCompatDatabase(configuration.name);
        this.database.attachHost(this);
    }

    /**
     * Blocks other threads until open callbacks finish (schema + configure + onOpen). The init
     * thread bypasses so onCreate/onUpgrade can run. Prevents a race where Room starts a background
     * query after the connection is bound but before schema callbacks complete.
     */
    void awaitReadyForExternalUse() {
        synchronized (this) {
            if (initialized) {
                return;
            }
            if (Thread.currentThread() == initializingThread) {
                return;
            }
            while (!initialized) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted waiting for database init", e);
                }
            }
        }
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
        if (initializing) {
            // Room may call getWritableDatabase() again from onConfigure on the same thread; connection
            // is already bound before onConfigure — do not wait() or we deadlock ourselves.
            if (Thread.currentThread() == initializingThread) {
                return;
            }
            while (!initialized) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted waiting for database init", e);
                }
            }
            return;
        }
        initializing = true;
        initializingThread = Thread.currentThread();
        try {
            initializeOpenHelperLocked();
        } finally {
            initializingThread = null;
            initializing = false;
            notifyAll();
        }
    }

    private void initializeOpenHelperLocked() {
        android.content.Context ctx = configuration.context;
        if (!SeekdbClient.isNativeAvailable()) {
            throw new IllegalStateException("SeekDB native library is not available");
        }
        String dbName = configuration.name == null ? "seekdb_android.db" : configuration.name;
        // Storage directory for embedded observer (aligned with historical Python embed: one root
        // per app databases dir). Per-file paths caused Room/OpenHelper DDL to diverge from runtime.
        String dbPath = configuration.context.getDatabasePath(dbName).getParent();
        int openRc = SeekdbClient.open(dbPath, 0);
        if (openRc != 0) {
            throw new IllegalStateException(
                    "seekdb_open failed: rc=" + openRc + " path=" + dbPath + " "
                            + SeekdbNativeBridge.nativeLastErrorMessage());
        }
        // C ABI `seekdb_connect(..., database, ...)` is the MySQL/OceanBase logical schema name
        // (not the SQLite file name). Embedded build currently expects the default `test` schema.
        connection = SeekdbClient.connect("test", true);
        database.setConnection(connection);
        SeekdbRuntime.installGlobalPrimary(connection);
        if (writeAheadLoggingEnabled) {
            database.enableWriteAheadLogging();
        }
        // Compat layer keeps schema version only in Java; each new OpenHelper instance
        // would otherwise see version==0 and re-run onCreate (heavy DDL) on every @Test.
        // Persist last successful schema version per database file path.
        android.content.SharedPreferences schemaPrefs =
                ctx.getSharedPreferences(SCHEMA_VERSION_PREFS, android.content.Context.MODE_PRIVATE);
        String schemaKey = ctx.getDatabasePath(dbName).getAbsolutePath();
        int persistedVersion = schemaPrefs.getInt(schemaKey, -1);
        if (persistedVersion >= 0) {
            database.setVersion(persistedVersion);
        }

        try {
            // Android SQLiteOpenHelper calls onConfigure before onCreate; Room may re-enter
            // getWritableDatabase() from onConfigure and run queries that need user tables while the
            // outer initializer has not run onCreate yet. Run schema callbacks first so tables exist.
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
            configuration.callback.onConfigure(database);
            configuration.callback.onOpen(database);
            // commit() so the next @Test's setUp sees a durable schema version (apply() is async).
            schemaPrefs.edit().putInt(schemaKey, database.getVersion()).commit();
        } catch (Throwable t) {
            throw t;
        }
        initialized = true;
    }

    @Override
    public void close() {
        database.close();
        // seekdb_close() here makes a later seekdb_open() in the same process unstable on Android
        // embed; native code switches storage when opening a different path. Release via
        // SeekdbCompat.shutdownEmbeddedEngine() when a full teardown is required.
    }
}
