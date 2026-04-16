package com.oceanbase.seekdb.android.compat;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SeekdbCompatDatabase implements SupportSQLiteDatabase {
    /**
     * Android SQLite uses {@code INSERT OR IGNORE INTO ...} /
     * {@code UPDATE OR IGNORE ...}.
     * OceanBase/MySQL expects {@code INSERT IGNORE INTO ...} /
     * {@code UPDATE IGNORE ...}; plain
     * {@code INSERT}/{@code UPDATE} must be separated from the table name (never
     * concatenate into
     * {@code INSERTINTO} / {@code UPDATEt}).
     */
    private final String path;
    private boolean open = true;
    /**
     * Nested levels (Room invalidation may begin/end before the app's transaction).
     */
    private int transactionDepth;

    private boolean transactionSuccessful;
    private int version = 0;
    private long pageSize = 4096L;
    private boolean writeAheadLoggingEnabled;
    private SeekdbConnection connection;
    private SeekdbOpenHelper hostHelper;
    private final SeekdbSessionManager sessionManager = new SeekdbSessionManager();
    private SQLiteTransactionListener activeTransactionListener;

    SeekdbCompatDatabase(String path) {
        this.path = path;
    }

    void attachHost(SeekdbOpenHelper helper) {
        this.hostHelper = helper;
    }

    private void awaitHostReadyIfNeeded() {
        if (hostHelper != null) {
            hostHelper.awaitReadyForExternalUse();
        }
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
        return new SeekdbCompatStatement(
                active.pointer(), SeekdbCompatSql.normalize(sql), active.nativeMutex());
    }

    @Override
    public void beginTransaction() {
        SeekdbConnection active = activeConnection();
        if (active == null || active.isClosed()) {
            throw new android.database.sqlite.SQLiteException("Database connection is not ready");
        }
        synchronized (active.nativeMutex()) {
            if (transactionDepth == 0) {
                SeekdbSqliteErrorMapper.throwIfError(active.begin(), "beginTransaction failed");
            }
            transactionDepth++;
            transactionSuccessful = false;
        }
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
        SQLiteTransactionListener listener = activeTransactionListener;
        activeTransactionListener = null;
        if (transactionDepth <= 0) {
            if (listener != null) {
                listener.onRollback();
            }
            throw new IllegalStateException("endTransaction called but no transaction is active");
        }
        SeekdbConnection active = activeConnection();
        if (active == null || active.isClosed()) {
            if (listener != null) {
                listener.onRollback();
            }
            return;
        }
        synchronized (active.nativeMutex()) {
            transactionDepth--;
            if (transactionDepth > 0) {
                transactionSuccessful = false;
                return;
            }
            try {
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
                transactionSuccessful = false;
            }
        }
    }

    @Override
    public void setTransactionSuccessful() {
        if (transactionDepth <= 0) {
            throw new IllegalStateException("setTransactionSuccessful called but no transaction is active");
        }
        transactionSuccessful = true;
    }

    @Override
    public boolean inTransaction() {
        return transactionDepth > 0;
    }

    @Override
    public boolean isDbLockedByCurrentThread() {
        // Degraded: no file lock introspection; in-transaction work is serialized on
        // this API surface.
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
        return queryInternal(query, bindArgs);
    }

    @Override
    public Cursor query(SupportSQLiteQuery query) {
        Cursor routed = maybeRoutePragmaQuery(query.getSql());
        if (routed != null) {
            return routed;
        }
        routed = maybeRouteRoomSqliteMasterQuery(query.getSql());
        if (routed != null) {
            return routed;
        }
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
        Cursor routed = maybeRoutePragmaQuery(query.getSql());
        if (routed != null) {
            return routed;
        }
        routed = maybeRouteRoomSqliteMasterQuery(query.getSql());
        if (routed != null) {
            return routed;
        }
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
        StringBuilder sql = new StringBuilder();
        if (conflictAlgorithm == 5) {
            // MySQL/OceanBase: REPLACE INTO … (SQLite: INSERT OR REPLACE INTO …)
            sql.append("REPLACE INTO ");
        } else if (conflictAlgorithm == 4) {
            // MySQL/OceanBase: INSERT IGNORE INTO … (SQLite: INSERT OR IGNORE INTO …)
            sql.append("INSERT IGNORE INTO ");
        } else {
            // CONFLICT_NONE and SQLite OR ROLLBACK/ABORT/FAIL: plain INSERT INTO
            sql.append("INSERT INTO ");
        }
        sql.append(table).append(" (");
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
        StringBuilder sql = new StringBuilder();
        if (conflictAlgorithm == 4) {
            // MySQL/OceanBase: UPDATE IGNORE tbl … (SQLite: UPDATE OR IGNORE tbl …)
            sql.append("UPDATE IGNORE ");
        } else {
            sql.append("UPDATE ");
        }
        sql.append(table).append(" SET ");
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
        if (isIgnoredSqlitePragmaExec(sql)) {
            return;
        }
        SupportSQLiteStatement statement = compileStatement(sql);
        try {
            statement.execute();
        } finally {
            closeStatement(statement);
        }
    }

    @Override
    public void execSQL(String sql, Object[] bindArgs) {
        if (isIgnoredSqlitePragmaExec(sql)) {
            return;
        }
        SupportSQLiteStatement statement = compileStatement(sql);
        try {
            bindArgs(statement, bindArgs);
            statement.execute();
        } finally {
            closeStatement(statement);
        }
    }

    /**
     * SQLite PRAGMAs Room may run at open; the embedded MySQL/OceanBase parser does
     * not support them.
     */
    private static boolean isIgnoredSqlitePragmaExec(String sql) {
        if (sql == null) {
            return false;
        }
        String u = sql.trim().toUpperCase(Locale.ROOT);
        return u.startsWith("PRAGMA TEMP_STORE")
                || u.startsWith("PRAGMA PAGE_SIZE")
                || u.startsWith("PRAGMA SYNCHRONOUS")
                || u.startsWith("PRAGMA JOURNAL_MODE")
                || u.startsWith("PRAGMA CACHE_SIZE")
                || u.startsWith("PRAGMA FOREIGN_KEYS")
                || u.startsWith("PRAGMA RECURSIVE_TRIGGERS");
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

    private static final Pattern PRAGMA_TABLE_INFO = Pattern
            .compile("(?is)^\\s*PRAGMA\\s+table_info\\s*\\(\\s*[`']?([^)`']+)[`']?\\s*\\)\\s*$");
    private static final Pattern PRAGMA_FOREIGN_KEY_LIST = Pattern
            .compile("(?is)^\\s*PRAGMA\\s+foreign_key_list\\s*\\(\\s*[`']?([^)`']+)[`']?\\s*\\)\\s*$");

    /**
     * Room uses SQLite {@code PRAGMA} introspection; map or stub the ones it needs
     * on MySQL/OceanBase.
     */
    private Cursor maybeRoutePragmaQuery(String sql) {
        if (sql == null) {
            return null;
        }
        String trimmed = sql.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (!upper.startsWith("PRAGMA")) {
            return null;
        }
        Matcher ti = PRAGMA_TABLE_INFO.matcher(trimmed);
        if (ti.matches()) {
            return cursorPragmaTableInfo(ti.group(1));
        }
        Matcher fk = PRAGMA_FOREIGN_KEY_LIST.matcher(trimmed);
        if (fk.matches()) {
            return cursorPragmaForeignKeyListEmpty();
        }
        if (upper.startsWith("PRAGMA INDEX_LIST")) {
            return new MatrixCursor(new String[0]);
        }
        if (upper.startsWith("PRAGMA INDEX_XINFO")) {
            return new MatrixCursor(new String[0]);
        }
        return null;
    }

    private Cursor cursorPragmaTableInfo(String rawTableName) {
        String table = rawTableName == null ? "" : rawTableName.trim();
        String sub = "SELECT (ORDINAL_POSITION - 1) AS cid, COLUMN_NAME AS name, "
                + "CASE "
                + "WHEN UPPER(COLUMN_TYPE) LIKE '%INT%' THEN 'INTEGER' "
                + "WHEN UPPER(COLUMN_TYPE) LIKE '%CHAR%' OR UPPER(COLUMN_TYPE) LIKE '%TEXT%' "
                + "THEN 'TEXT' "
                + "WHEN UPPER(COLUMN_TYPE) LIKE '%BLOB%' THEN 'BLOB' "
                + "WHEN UPPER(COLUMN_TYPE) LIKE '%REAL%' OR UPPER(COLUMN_TYPE) LIKE '%FLOA%' "
                + "OR UPPER(COLUMN_TYPE) LIKE '%DOUB%' THEN 'REAL' "
                + "ELSE UPPER(COLUMN_TYPE) END AS type, "
                + "IF(IS_NULLABLE = 'NO', 1, 0) AS notnull, COLUMN_DEFAULT AS dflt_value, "
                + "IF(COLUMN_KEY = 'PRI', 1, 0) AS pk "
                + "FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? "
                + "ORDER BY ORDINAL_POSITION";
        return queryDirect(sub, new Object[] { table });
    }

    private static Cursor cursorPragmaForeignKeyListEmpty() {
        return new MatrixCursor(
                new String[] { "id", "seq", "table", "on_delete", "on_update", "from", "to" });
    }

    private Cursor queryDirect(String sql, Object[] bindArgs) {
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

    /**
     * Room's {@code RoomOpenHelper} queries {@code sqlite_master}, which does not
     * exist on
     * MySQL/OceanBase. Map the two catalog probes it uses to
     * {@code information_schema.tables}.
     */
    private Cursor maybeRouteRoomSqliteMasterQuery(String sql) {
        if (sql == null) {
            return null;
        }
        String upper = sql.trim().toUpperCase(Locale.ROOT);
        if (!upper.contains("SQLITE_MASTER")) {
            return null;
        }
        if (upper.contains("SELECT 1 FROM SQLITE_MASTER")
                && upper.contains("TYPE")
                && upper.contains("TABLE")
                && upper.contains("ROOM_MASTER_TABLE")) {
            return cursorRoomHasRoomMasterTable();
        }
        if (upper.contains("SELECT COUNT(*) FROM SQLITE_MASTER")
                && upper.contains("ANDROID_METADATA")) {
            return cursorRoomSqliteMasterNonAndroidMetadataCount();
        }
        return null;
    }

    private Cursor cursorRoomHasRoomMasterTable() {
        SeekdbCompatStatement st = (SeekdbCompatStatement) compileStatement(
                "SELECT COUNT(*) FROM information_schema.tables WHERE "
                        + "TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'room_master_table'");
        try {
            long n = st.simpleQueryForLong();
            MatrixCursor c = new MatrixCursor(new String[] { "1" });
            if (n > 0L) {
                c.addRow(new Object[] { 1 });
            }
            return c;
        } finally {
            st.close();
        }
    }

    private Cursor cursorRoomSqliteMasterNonAndroidMetadataCount() {
        SeekdbCompatStatement st = (SeekdbCompatStatement) compileStatement(
                "SELECT COUNT(*) FROM information_schema.tables WHERE "
                        + "TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE' "
                        + "AND TABLE_NAME <> 'android_metadata'");
        try {
            long n = st.simpleQueryForLong();
            MatrixCursor c = new MatrixCursor(new String[] { "count(*)" });
            c.addRow(new Object[] { n });
            return c;
        } finally {
            st.close();
        }
    }

    private Cursor queryInternal(String sql, Object[] bindArgs) {
        Cursor routed = maybeRoutePragmaQuery(sql);
        if (routed != null) {
            return routed;
        }
        routed = maybeRouteRoomSqliteMasterQuery(sql);
        if (routed != null) {
            return routed;
        }
        return queryDirect(sql, bindArgs);
    }

    private SeekdbConnection activeConnection() {
        awaitHostReadyIfNeeded();
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
