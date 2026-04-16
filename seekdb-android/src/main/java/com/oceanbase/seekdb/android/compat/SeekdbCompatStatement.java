package com.oceanbase.seekdb.android.compat;

import com.oceanbase.seekdb.android.core.SeekdbNativeBridge;
import com.oceanbase.seekdb.android.database.SeekdbWindowedCursor;
import com.oceanbase.seekdb.android.runtime.SeekdbStreamingPolicy;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteException;
import android.os.CancellationSignal;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class SeekdbCompatStatement implements SupportSQLiteStatement {
    /**
     * OceanBase {@code OB_EAGAIN} — user message is "Try again"; safe to retry
     * execute after stmt reset.
     */
    private static final int OB_EAGAIN = -4023;

    /**
     * Bootstrap DDL can hit many transient {@link #OB_EAGAIN}s; keep bounded but
     * sufficient for embed.
     */
    private static final int EAGAIN_STMT_EXECUTE_MAX_ATTEMPTS = 128;

    private static final byte TYPE_NULL = 0;
    private static final byte TYPE_LONG = 1;
    private static final byte TYPE_DOUBLE = 2;
    private static final byte TYPE_STRING = 3;
    private static final byte TYPE_BLOB = 4;

    private final long connectionPtr;
    private final String sql;
    /**
     * Same object as
     * {@link com.oceanbase.seekdb.android.nativeapi.SeekdbConnection#nativeMutex()}.
     */
    private final Object nativeMutex;
    /**
     * Room insert: first column {@code id}, bind {@code 0} → native NULL for
     * {@code AUTO_INCREMENT}.
     */
    private final boolean bindFirstLongZeroAsNullForRoomInsert;
    private final Map<Integer, Object> values = new HashMap<>();
    private final Map<Integer, Byte> valueTypes = new HashMap<>();

    SeekdbCompatStatement(long connectionPtr, String sql, Object nativeMutex) {
        this.connectionPtr = connectionPtr;
        this.sql = sql;
        this.nativeMutex = nativeMutex;
        this.bindFirstLongZeroAsNullForRoomInsert = SeekdbCompatSql.isRoomInsertLeadingBacktickIdColumn(sql);
    }

    /**
     * DDL may still expose non-null result metadata with {@code columnCount > 0};
     * pulling rows then
     * hits native EOF / corrupt cursor state (see comment below). Skip row fetch
     * for plain DDL.
     */
    private void checkStmtExecuteRc(long connectionPtr, long stmt) {
        int execRc = SeekdbNativeBridge.nativeStmtLastExecuteRc(stmt);
        if (execRc == SeekdbSqliteErrorMapper.SEEKDB_SUCCESS) {
            return;
        }
        int last = SeekdbNativeBridge.nativeLastErrorCode();
        int rc = last != SeekdbSqliteErrorMapper.SEEKDB_SUCCESS ? last : execRc;
        int obErr = SeekdbNativeBridge.nativeErrno(connectionPtr);
        String errMsg = SeekdbNativeBridge.nativeLastErrorMessage();
        String head = (errMsg != null && !errMsg.isEmpty()) ? errMsg : "seekdb_stmt_execute failed";
        String detail = SeekdbCompatDiagnostics.formatStmtExecuteFailure(
                connectionPtr, sql, execRc, last, rc, obErr);
        String full = head + detail;
        throw SeekdbSqliteErrorMapper.fromRc(rc, full, optionalSqlState(connectionPtr));
    }

    /**
     * Runs {@link SeekdbNativeBridge#nativeStmtExecute(long)} with a small bounded
     * retry when the
     * engine reports {@link #OB_EAGAIN} (compat: "Try again"), which can occur
     * under transient
     * tablet/MDS contention on Android embed. Retries are immediate re-executes (no
     * {@link SeekdbNativeBridge#nativeStmtReset(long)}); reset+rebind was observed
     * to destabilize
     * some embed paths.
     */
    private long executeStmtWithEagainRetry(long stmt) {
        long result = 0L;
        for (int attempt = 0; attempt < EAGAIN_STMT_EXECUTE_MAX_ATTEMPTS; attempt++) {
            if (attempt > 0) {
                Thread.yield();
            }
            result = SeekdbNativeBridge.nativeStmtExecute(stmt);
            int execRc = SeekdbNativeBridge.nativeStmtLastExecuteRc(stmt);
            if (execRc == SeekdbSqliteErrorMapper.SEEKDB_SUCCESS) {
                return result;
            }
            int last = SeekdbNativeBridge.nativeLastErrorCode();
            int rc = last != SeekdbSqliteErrorMapper.SEEKDB_SUCCESS ? last : execRc;
            int obErr = SeekdbNativeBridge.nativeErrno(connectionPtr);
            String errMsg = SeekdbNativeBridge.nativeLastErrorMessage();
            final boolean lastAttempt = attempt == EAGAIN_STMT_EXECUTE_MAX_ATTEMPTS - 1;
            // Embed may surface OB_EAGAIN (-4023) on stmt return / last code while
            // connection errno lags.
            final boolean tryAgainTransient = obErr == OB_EAGAIN
                    || execRc == OB_EAGAIN
                    || last == OB_EAGAIN
                    || (errMsg != null
                            && errMsg.toUpperCase(Locale.ROOT).contains("TRY AGAIN"));
            if (!tryAgainTransient || lastAttempt) {
                String head = (errMsg != null && !errMsg.isEmpty()) ? errMsg : "seekdb_stmt_execute failed";
                String detail = SeekdbCompatDiagnostics.formatStmtExecuteFailure(
                        connectionPtr, sql, execRc, last, rc, obErr);
                String full = head + detail;
                throw SeekdbSqliteErrorMapper.fromRc(
                        rc, full, optionalSqlState(connectionPtr));
            }
        }
        throw new IllegalStateException("seekdb_stmt_execute retry loop fell through");
    }

    private static boolean isDdlSql(String sql) {
        if (sql == null) {
            return false;
        }
        String t = sql.trim();
        while (t.startsWith("--")) {
            int nl = t.indexOf('\n');
            if (nl < 0) {
                return false;
            }
            t = t.substring(nl + 1).trim();
        }
        if (t.isEmpty()) {
            return false;
        }
        if (t.length() >= 6 && t.regionMatches(true, 0, "CREATE", 0, 6)) {
            return true;
        }
        if (t.length() >= 4 && t.regionMatches(true, 0, "DROP", 0, 4)) {
            return true;
        }
        if (t.length() >= 5 && t.regionMatches(true, 0, "ALTER", 0, 5)) {
            return true;
        }
        if (t.length() >= 8 && t.regionMatches(true, 0, "TRUNCATE", 0, 8)) {
            return true;
        }
        if (t.length() >= 6 && t.regionMatches(true, 0, "RENAME", 0, 6)) {
            return true;
        }
        return false;
    }

    @Override
    public void execute() {
        executeInternal();
    }

    @Override
    public int executeUpdateDelete() {
        return (int) executeInternal().affectedRows;
    }

    @Override
    public long executeInsert() {
        long id = executeInternal().insertId;
        return id == 0L ? -1L : id;
    }

    @Override
    public long simpleQueryForLong() {
        ExecOutcome outcome = executeInternal();
        if (outcome.firstValue == null) {
            throw new SQLiteDoneException("No rows returned");
        }
        return parseIntegralResult(outcome.firstValue);
    }

    /**
     * OceanBase may return numeric cells as {@code "5.0"}; Room COUNT(*) paths need
     * a whole number.
     */
    private static long parseIntegralResult(String value) {
        String t = value.trim();
        try {
            return Long.parseLong(t);
        } catch (NumberFormatException e) {
            return (long) Double.parseDouble(t);
        }
    }

    @Override
    public String simpleQueryForString() {
        ExecOutcome outcome = executeInternal();
        if (outcome.firstValue == null) {
            throw new SQLiteDoneException("No rows returned");
        }
        return outcome.firstValue;
    }

    Cursor executeQueryCursor() {
        return executeQueryCursor(null);
    }

    Cursor executeQueryCursor(CancellationSignal cancellationSignal) {
        synchronized (nativeMutex) {
            long stmt = SeekdbNativeBridge.nativeStmtPrepare(connectionPtr, sql);
            if (stmt == 0L) {
                throwStmtPrepareFailed();
            }
            boolean cursorOwnsStmt = false;
            try {
                bindAllParameters(stmt);
                long result = executeStmtWithEagainRetry(stmt);
                checkStmtExecuteRc(connectionPtr, stmt);
                if (result == 0L) {
                    return new MatrixCursor(new String[0]);
                }
                String[] columns = readColumnNames(result);
                if (columns.length == 0) {
                    return new MatrixCursor(new String[0]);
                }
                if (SeekdbStreamingPolicy.useStreamingQueryCursors()) {
                    cursorOwnsStmt = true;
                    return new SeekdbWindowedCursor(stmt, result, columns, cancellationSignal);
                }
                Object[][] rows = cancellationSignal == null
                        ? SeekdbNativeBridge.nativeResultFetchAllTyped(result)
                        : SeekdbNativeBridge.nativeResultFetchAllTyped(result, cancellationSignal);
                if (rows == null) {
                    int rc = SeekdbNativeBridge.nativeLastErrorCode();
                    if (rc != SeekdbSqliteErrorMapper.SEEKDB_SUCCESS) {
                        throw SeekdbSqliteErrorMapper.fromRc(
                                rc, "seekdb result fetch failed", connectionPtr);
                    }
                    throw new SQLiteException("nativeResultFetchAllTyped returned null");
                }
                // Result is owned by the native statement (stmt_data->result_set);
                // seekdb_result_free()
                // would delete it and leave a dangling pointer for ~SeekdbStmtData
                // (double-free).
                MatrixCursor cursor = new MatrixCursor(columns);
                if (rows != null) {
                    for (Object[] row : rows) {
                        cursor.addRow(row);
                    }
                }
                return cursor;
            } finally {
                if (!cursorOwnsStmt) {
                    SeekdbNativeBridge.nativeStmtClose(stmt);
                }
            }
        }
    }

    @Override
    public void bindNull(int index) {
        synchronized (nativeMutex) {
            valueTypes.put(index, TYPE_NULL);
            values.remove(index);
        }
    }

    @Override
    public void bindLong(int index, long value) {
        synchronized (nativeMutex) {
            if (bindFirstLongZeroAsNullForRoomInsert && index == 1 && value == 0L) {
                valueTypes.put(index, TYPE_NULL);
                values.remove(index);
                return;
            }
            valueTypes.put(index, TYPE_LONG);
            values.put(index, value);
        }
    }

    @Override
    public void bindDouble(int index, double value) {
        synchronized (nativeMutex) {
            valueTypes.put(index, TYPE_DOUBLE);
            values.put(index, value);
        }
    }

    @Override
    public void bindString(int index, String value) {
        synchronized (nativeMutex) {
            valueTypes.put(index, TYPE_STRING);
            values.put(index, value);
        }
    }

    @Override
    public void bindBlob(int index, byte[] value) {
        synchronized (nativeMutex) {
            valueTypes.put(index, TYPE_BLOB);
            values.put(index, value);
        }
    }

    @Override
    public void clearBindings() {
        synchronized (nativeMutex) {
            valueTypes.clear();
            values.clear();
        }
    }

    @Override
    public void close() {
        synchronized (nativeMutex) {
            clearBindings();
        }
    }

    private ExecOutcome executeInternal() {
        return executeInternal(null);
    }

    private ExecOutcome executeInternal(CancellationSignal cancellationSignal) {
        synchronized (nativeMutex) {
            long stmt = SeekdbNativeBridge.nativeStmtPrepare(connectionPtr, sql);
            if (stmt == 0L) {
                throwStmtPrepareFailed();
            }
            ExecOutcome outcome = new ExecOutcome();
            try {
                bindAllParameters(stmt);
                long result = executeStmtWithEagainRetry(stmt);
                checkStmtExecuteRc(connectionPtr, stmt);
                outcome.affectedRows = SeekdbNativeBridge.nativeStmtAffectedRows(stmt);
                outcome.insertId = SeekdbNativeBridge.nativeStmtInsertId(stmt);
                if (result != 0L) {
                    int columnCount = SeekdbNativeBridge.nativeResultColumnCount(result);
                    final boolean ddl = isDdlSql(sql);
                    if (columnCount > 0 && !ddl) {
                        outcome.columns = new String[columnCount];
                        for (int i = 0; i < columnCount; i++) {
                            String name = SeekdbNativeBridge.nativeResultColumnName(result, i);
                            outcome.columns[i] = name == null ? ("col_" + i) : name;
                        }
                        Object[][] rows = cancellationSignal == null
                                ? SeekdbNativeBridge.nativeResultFetchAllTyped(result)
                                : SeekdbNativeBridge.nativeResultFetchAllTyped(
                                        result, cancellationSignal);
                        if (rows == null) {
                            int rc = SeekdbNativeBridge.nativeLastErrorCode();
                            if (rc != SeekdbSqliteErrorMapper.SEEKDB_SUCCESS) {
                                throw SeekdbSqliteErrorMapper.fromRc(
                                        rc, "seekdb result fetch failed", connectionPtr);
                            }
                            throw new SQLiteException("nativeResultFetchAllTyped returned null");
                        }
                        outcome.rows = rows;
                        if (rows.length > 0 && rows[0] != null && rows[0].length > 0) {
                            outcome.firstValue = rows[0][0] == null ? null : String.valueOf(rows[0][0]);
                        }
                    } else {
                        // DDL / OK packets can expose a non-null result with 0 columns; fetching rows
                        // then loops native EOF incorrectly and can hang or kill the process.
                        // Engine may also report columnCount > 0 for DDL metadata; never pull rows for
                        // DDL.
                        outcome.columns = new String[0];
                        outcome.rows = null;
                    }
                    // Do not nativeResultFree(result): same ownership as executeQueryCursor().
                }
                return outcome;
            } finally {
                SeekdbNativeBridge.nativeStmtClose(stmt);
            }
        }
    }

    private void bindAllParameters(long stmt) {
        List<Integer> keys = new ArrayList<>(valueTypes.keySet());
        Collections.sort(keys);
        for (Integer key : keys) {
            final int index0 = key - 1;
            final byte type = valueTypes.get(key);
            int rc;
            if (type == TYPE_NULL) {
                rc = SeekdbNativeBridge.nativeStmtBindNull(stmt, index0);
            } else if (type == TYPE_LONG) {
                rc = SeekdbNativeBridge.nativeStmtBindLong(stmt, index0, (Long) values.get(key));
            } else if (type == TYPE_DOUBLE) {
                rc = SeekdbNativeBridge.nativeStmtBindDouble(stmt, index0, (Double) values.get(key));
            } else if (type == TYPE_STRING) {
                rc = SeekdbNativeBridge.nativeStmtBindString(stmt, index0, (String) values.get(key));
            } else if (type == TYPE_BLOB) {
                rc = SeekdbNativeBridge.nativeStmtBindBlob(stmt, index0, (byte[]) values.get(key));
            } else {
                rc = -1;
            }
            if (rc != 0) {
                String nmsg = SeekdbNativeBridge.nativeLastErrorMessage();
                String head = (nmsg != null && !nmsg.isEmpty())
                        ? nmsg
                        : "seekdb_stmt_bind_param failed";
                String full = head + SeekdbCompatDiagnostics.formatBindFailure(sql, key, rc);
                throw SeekdbSqliteErrorMapper.fromRc(
                        rc, full, optionalSqlState(connectionPtr));
            }
        }
    }

    private void throwStmtPrepareFailed() {
        int prc = SeekdbNativeBridge.nativeLastErrorCode();
        String nmsg = SeekdbNativeBridge.nativeLastErrorMessage();
        String head = (nmsg != null && !nmsg.isEmpty()) ? nmsg : "seekdb_stmt_prepare failed";
        String full = head + SeekdbCompatDiagnostics.formatPrepareFailure(sql, prc);
        throw SeekdbSqliteErrorMapper.fromRc(prc, full, optionalSqlState(connectionPtr));
    }

    private static String optionalSqlState(long connectionPtr) {
        if (connectionPtr == 0L) {
            return null;
        }
        try {
            String s = SeekdbNativeBridge.nativeLastSqlState(connectionPtr);
            if (s == null || s.isEmpty()) {
                return null;
            }
            return s;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String[] readColumnNames(long result) {
        int columnCount = SeekdbNativeBridge.nativeResultColumnCount(result);
        if (columnCount <= 0) {
            return new String[0];
        }
        String[] columns = new String[columnCount];
        for (int i = 0; i < columnCount; i++) {
            String name = SeekdbNativeBridge.nativeResultColumnName(result, i);
            columns[i] = name == null ? ("col_" + i) : name;
        }
        return columns;
    }

    private static final class ExecOutcome {
        long affectedRows;
        long insertId;
        String firstValue;
        String[] columns;
        Object[][] rows;
    }
}
