package com.oceanbase.seekdb.android.compat;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteTransactionListener;
import android.os.OperationCanceledException;
import android.os.CancellationSignal;
import android.util.Pair;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.oceanbase.seekdb.android.runtime.SeekdbRuntime;
import com.oceanbase.seekdb.android.runtime.SeekdbSessionManager;
import com.oceanbase.seekdb.android.nativeapi.SeekdbConnection;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class SeekdbCompatDatabase implements SupportSQLiteDatabase {
    private static final String[] CONFLICT_VALUES = {
            "",
            " OR ROLLBACK ",
            " OR ABORT ",
            " OR FAIL ",
            " OR IGNORE ",
            " OR REPLACE "
    };
    private final String path;
    private boolean open = true;
    private boolean transactionOpen;
    private boolean transactionSuccessful;
    private int version = 0;
    private long pageSize = 4096L;
    private boolean writeAheadLoggingEnabled;
    private SeekdbConnection connection;
    private final SeekdbSessionManager sessionManager = new SeekdbSessionManager();
    private SQLiteTransactionListener activeTransactionListener;

    SeekdbCompatDatabase(String path) {
        this.path = path;
    }

    void setConnection(SeekdbConnection connection) {
        this.connection = connection;
        this.sessionManager.setSharedConnection(connection);
    }

    @Override
    public SupportSQLiteStatement compileStatement(String sql) {
        if (!open) {
            throw new android.database.sqlite.SQLiteException("Database is closed");
        }
        SeekdbConnection active = activeConnection();
        if (active == null || active.isClosed()) {
            throw new android.database.sqlite.SQLiteException("Database connection is not ready");
        }
        return new SeekdbCompatStatement(active.pointer(), sql);
    }

    @Override
    public void beginTransaction() {
        if (transactionOpen) {
            throw new IllegalStateException(
                    "Nested transactions are not supported; finish the current transaction first.");
        }
        SeekdbConnection active = activeConnection();
        if (active != null && !active.isClosed()) {
            SeekdbSqliteErrorMapper.throwIfError(active.begin(), "beginTransaction failed");
        }
        transactionOpen = true;
        transactionSuccessful = false;
    }

    @Override
    public void beginTransactionNonExclusive() {
        beginTransaction();
    }

    @Override
    public void beginTransactionWithListener(SQLiteTransactionListener transactionListener) {
        beginTransaction();
        activeTransactionListener = transactionListener;
        if (transactionListener != null) {
            transactionListener.onBegin();
        }
    }

    @Override
    public void beginTransactionWithListenerNonExclusive(SQLiteTransactionListener transactionListener) {
        beginTransactionWithListener(transactionListener);
    }

    @Override
    public void endTransaction() {
        if (!transactionOpen) {
            throw new IllegalStateException("endTransaction called but no transaction is active");
        }
        SQLiteTransactionListener listener = activeTransactionListener;
        activeTransactionListener = null;
        SeekdbConnection active = activeConnection();
        try {
            if (active == null || active.isClosed()) {
                if (listener != null) {
                    listener.onRollback();
                }
                return;
            }
            if (transactionSuccessful) {
                SeekdbSqliteErrorMapper.throwIfError(active.commit(), "commit failed");
                if (listener != null) {
                    listener.onCommit();
                }
            } else {
                SeekdbSqliteErrorMapper.throwIfError(active.rollback(), "rollback failed");
                if (listener != null) {
                    listener.onRollback();
                }
            }
        } finally {
            transactionOpen = false;
            transactionSuccessful = false;
        }
    }

    @Override
    public void setTransactionSuccessful() {
        if (!transactionOpen) {
            throw new IllegalStateException("setTransactionSuccessful called but no transaction is active");
        }
        transactionSuccessful = true;
    }

    @Override
    public boolean inTransaction() {
        return transactionOpen;
    }

    @Override
    public boolean isDbLockedByCurrentThread() {
        // Degraded: no file lock introspection; in-transaction work is serialized on this API surface.
        return inTransaction();
    }

    @Override
    public boolean yieldIfContendedSafely() {
        return false;
    }

    @Override
    public boolean yieldIfContendedSafely(long sleepAfterYieldDelayMillis) {
        return false;
    }

    @Override
    public boolean isExecPerConnectionSQLSupported() {
        return false;
    }

    @Override
    public void execPerConnectionSQL(String sql, @SuppressLint("ArrayReturn") Object[] bindArgs) {
        execSQL(sql, bindArgs);
    }

    @Override
    public long getMaximumSize() {
        throw new UnsupportedOperationException(
                "SeekDB Android: maximum database size is not exposed via this API; use host file limits.");
    }

    @Override
    public int getVersion() {
        return version;
    }

    @Override
    public void setVersion(int version) {
        this.version = version;
    }

    @Override
    public long setMaximumSize(long numBytes) {
        throw new UnsupportedOperationException(
                "SeekDB Android: setMaximumSize is not supported; configure storage outside this API.");
    }

    @Override
    public long getPageSize() {
        return pageSize;
    }

    @Override
    public void setPageSize(long numBytes) {
        this.pageSize = numBytes;
    }

    @Override
    public Cursor query(String query) {
        return queryInternal(query, null);
    }

    @Override
    public Cursor query(String query, Object[] bindArgs) {
        SeekdbCompatStatement statement = (SeekdbCompatStatement) compileStatement(query);
        try {
            bindArgs(statement, bindArgs);
            return statement.executeQueryCursor();
        } finally {
            statement.close();
        }
    }

    @Override
    public Cursor query(SupportSQLiteQuery query) {
        SeekdbCompatStatement statement = (SeekdbCompatStatement) compileStatement(query.getSql());
        try {
            query.bindTo(statement);
            return statement.executeQueryCursor();
        } finally {
            statement.close();
        }
    }

    @Override
    public Cursor query(SupportSQLiteQuery query, CancellationSignal cancellationSignal) {
        throwIfCanceled(cancellationSignal);
        SeekdbCompatStatement statement = (SeekdbCompatStatement) compileStatement(query.getSql());
        try {
            query.bindTo(statement);
            throwIfCanceled(cancellationSignal);
            return statement.executeQueryCursor(cancellationSignal);
        } finally {
            statement.close();
        }
    }

    @Override
    public long insert(String table, int conflictAlgorithm, ContentValues values) {
        if (values == null || values.size() == 0) {
            throw new IllegalArgumentException("Empty values for insert");
        }
        String conflict = conflictAlgorithm >= 0 && conflictAlgorithm < CONFLICT_VALUES.length
                ? CONFLICT_VALUES[conflictAlgorithm]
                : "";
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT").append(conflict).append("INTO ").append(table).append(" (");
        StringBuilder placeholders = new StringBuilder();
        Object[] bindArgs = new Object[values.size()];
        int i = 0;
        for (Map.Entry<String, Object> entry : values.valueSet()) {
            if (i > 0) {
                sql.append(", ");
                placeholders.append(", ");
            }
            sql.append(entry.getKey());
            placeholders.append("?");
            bindArgs[i++] = entry.getValue();
        }
        sql.append(") VALUES (").append(placeholders).append(")");
        SupportSQLiteStatement statement = compileStatement(sql.toString());
        try {
            bindArgs(statement, bindArgs);
            return statement.executeInsert();
        } finally {
            closeStatement(statement);
        }
    }

    @Override
    public int delete(String table, String whereClause, Object[] whereArgs) {
        StringBuilder sql = new StringBuilder();
        sql.append("DELETE FROM ").append(table);
        if (whereClause != null && !whereClause.isEmpty()) {
            sql.append(" WHERE ").append(whereClause);
        }
        SupportSQLiteStatement statement = compileStatement(sql.toString());
        try {
            bindArgs(statement, whereArgs);
            return statement.executeUpdateDelete();
        } finally {
            closeStatement(statement);
        }
    }

    @Override
    public int update(String table, int conflictAlgorithm, ContentValues values, String whereClause,
            Object[] whereArgs) {
        if (values == null || values.size() == 0) {
            throw new IllegalArgumentException("Empty values for update");
        }
        String conflict = conflictAlgorithm >= 0 && conflictAlgorithm < CONFLICT_VALUES.length
                ? CONFLICT_VALUES[conflictAlgorithm]
                : "";
        StringBuilder sql = new StringBuilder();
        sql.append("UPDATE").append(conflict).append(table).append(" SET ");
        Object[] bindArgs = new Object[values.size() + (whereArgs == null ? 0 : whereArgs.length)];
        int i = 0;
        for (Map.Entry<String, Object> entry : values.valueSet()) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(entry.getKey()).append("=?");
            bindArgs[i++] = entry.getValue();
        }
        if (whereClause != null && !whereClause.isEmpty()) {
            sql.append(" WHERE ").append(whereClause);
        }
        if (whereArgs != null) {
            for (Object arg : whereArgs) {
                bindArgs[i++] = arg;
            }
        }
        SupportSQLiteStatement statement = compileStatement(sql.toString());
        try {
            bindArgs(statement, bindArgs);
            return statement.executeUpdateDelete();
        } finally {
            closeStatement(statement);
        }
    }

    @Override
    public void execSQL(String sql) {
        SupportSQLiteStatement statement = compileStatement(sql);
        try {
            statement.execute();
        } finally {
            closeStatement(statement);
        }
    }

    @Override
    public void execSQL(String sql, Object[] bindArgs) {
        SupportSQLiteStatement statement = compileStatement(sql);
        try {
            bindArgs(statement, bindArgs);
            statement.execute();
        } finally {
            closeStatement(statement);
        }
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public boolean needUpgrade(int newVersion) {
        return version < newVersion;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public void setLocale(Locale locale) {
    }

    @Override
    public void setMaxSqlCacheSize(int cacheSize) {
    }

    @Override
    public void setForeignKeyConstraintsEnabled(boolean enabled) {
        SeekdbConnection active = activeConnection();
        if (active == null || active.isClosed()) {
            return;
        }
        execSQL("PRAGMA foreign_keys = " + (enabled ? "ON" : "OFF"));
    }

    @Override
    public boolean enableWriteAheadLogging() {
        writeAheadLoggingEnabled = true;
        return true;
    }

    @Override
    public void disableWriteAheadLogging() {
        writeAheadLoggingEnabled = false;
    }

    @Override
    public boolean isWriteAheadLoggingEnabled() {
        return writeAheadLoggingEnabled;
    }

    @Override
    public List<Pair<String, String>> getAttachedDbs() {
        return Collections.emptyList();
    }

    @Override
    public boolean isDatabaseIntegrityOk() {
        // Degraded: no PRAGMA integrity_check wiring yet; false means "not verified".
        return false;
    }

    @Override
    public void close() {
        if (connection != null) {
            connection.close();
            connection = null;
        }
        SeekdbRuntime.installGlobalPrimary(null);
        sessionManager.clearThreadSession();
        open = false;
    }

    private Cursor queryInternal(String sql, Object[] bindArgs) {
        SeekdbConnection active = activeConnection();
        if (active == null || active.isClosed()) {
            throw new android.database.sqlite.SQLiteException("Database connection is not ready");
        }
        SeekdbCompatStatement statement = (SeekdbCompatStatement) compileStatement(sql);
        try {
            bindArgs(statement, bindArgs);
            return statement.executeQueryCursor();
        } finally {
            statement.close();
        }
    }

    private SeekdbConnection activeConnection() {
        return sessionManager.currentConnection();
    }

    private static void bindArgs(SupportSQLiteStatement statement, Object[] bindArgs) {
        if (bindArgs == null) {
            return;
        }
        for (int i = 0; i < bindArgs.length; i++) {
            int index = i + 1;
            Object arg = bindArgs[i];
            if (arg == null) {
                statement.bindNull(index);
            } else if (arg instanceof byte[]) {
                statement.bindBlob(index, (byte[]) arg);
            } else if (arg instanceof Float || arg instanceof Double) {
                statement.bindDouble(index, ((Number) arg).doubleValue());
            } else if (arg instanceof Number || arg instanceof Boolean) {
                long v = arg instanceof Boolean ? (((Boolean) arg) ? 1L : 0L) : ((Number) arg).longValue();
                statement.bindLong(index, v);
            } else {
                statement.bindString(index, String.valueOf(arg));
            }
        }
    }

    private static void closeStatement(SupportSQLiteStatement statement) {
        try {
            statement.close();
        } catch (IOException e) {
            throw new android.database.sqlite.SQLiteException("Failed to close statement", e);
        }
    }

    private static void throwIfCanceled(CancellationSignal cancellationSignal) {
        if (cancellationSignal != null && cancellationSignal.isCanceled()) {
            throw new OperationCanceledException();
        }
    }
}
