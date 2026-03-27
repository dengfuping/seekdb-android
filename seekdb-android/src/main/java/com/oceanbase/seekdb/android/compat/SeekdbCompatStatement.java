package com.oceanbase.seekdb.android.compat;

import com.oceanbase.seekdb.android.core.SeekdbNativeBridge;
import com.oceanbase.seekdb.android.database.SeekdbWindowedCursor;
import com.oceanbase.seekdb.android.runtime.SeekdbStreamingPolicy;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDoneException;
import android.os.CancellationSignal;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class SeekdbCompatStatement implements SupportSQLiteStatement {
    private static final byte TYPE_NULL = 0;
    private static final byte TYPE_LONG = 1;
    private static final byte TYPE_DOUBLE = 2;
    private static final byte TYPE_STRING = 3;
    private static final byte TYPE_BLOB = 4;

    private final long connectionPtr;
    private final String sql;
    private final Map<Integer, Object> values = new HashMap<>();
    private final Map<Integer, Byte> valueTypes = new HashMap<>();

    SeekdbCompatStatement(long connectionPtr, String sql) {
        this.connectionPtr = connectionPtr;
        this.sql = sql;
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
        return Long.parseLong(outcome.firstValue);
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
        long stmt = SeekdbNativeBridge.nativeStmtPrepare(connectionPtr, sql);
        if (stmt == 0L) {
            throw SeekdbSqliteErrorMapper.fromRc(
                    SeekdbNativeBridge.nativeLastErrorCode(),
                    "seekdb_stmt_prepare failed");
        }
        boolean cursorOwnsStmt = false;
        try {
            bindAllParameters(stmt);
            long result = SeekdbNativeBridge.nativeStmtExecute(stmt);
            if (result == 0L && SeekdbNativeBridge.nativeLastErrorCode() != SeekdbSqliteErrorMapper.SEEKDB_SUCCESS) {
                throw SeekdbSqliteErrorMapper.fromRc(
                        SeekdbNativeBridge.nativeLastErrorCode(),
                        "seekdb_stmt_execute failed");
            }
            if (result == 0L) {
                return new MatrixCursor(new String[0]);
            }
            String[] columns = readColumnNames(result);
            if (SeekdbStreamingPolicy.useStreamingQueryCursors()) {
                cursorOwnsStmt = true;
                return new SeekdbWindowedCursor(stmt, result, columns, cancellationSignal);
            }
            Object[][] rows =
                    cancellationSignal == null
                            ? SeekdbNativeBridge.nativeResultFetchAllTyped(result)
                            : SeekdbNativeBridge.nativeResultFetchAllTyped(result, cancellationSignal);
            SeekdbNativeBridge.nativeResultFree(result);
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

    @Override
    public void bindNull(int index) {
        valueTypes.put(index, TYPE_NULL);
        values.remove(index);
    }

    @Override
    public void bindLong(int index, long value) {
        valueTypes.put(index, TYPE_LONG);
        values.put(index, value);
    }

    @Override
    public void bindDouble(int index, double value) {
        valueTypes.put(index, TYPE_DOUBLE);
        values.put(index, value);
    }

    @Override
    public void bindString(int index, String value) {
        valueTypes.put(index, TYPE_STRING);
        values.put(index, value);
    }

    @Override
    public void bindBlob(int index, byte[] value) {
        valueTypes.put(index, TYPE_BLOB);
        values.put(index, value);
    }

    @Override
    public void clearBindings() {
        valueTypes.clear();
        values.clear();
    }

    @Override
    public void close() {
        clearBindings();
    }

    private ExecOutcome executeInternal() {
        return executeInternal(null);
    }

    private ExecOutcome executeInternal(CancellationSignal cancellationSignal) {
        long stmt = SeekdbNativeBridge.nativeStmtPrepare(connectionPtr, sql);
        if (stmt == 0L) {
            throw SeekdbSqliteErrorMapper.fromRc(
                    SeekdbNativeBridge.nativeLastErrorCode(),
                    "seekdb_stmt_prepare failed");
        }
        ExecOutcome outcome = new ExecOutcome();
        try {
            bindAllParameters(stmt);
            long result = SeekdbNativeBridge.nativeStmtExecute(stmt);
            if (result == 0L && SeekdbNativeBridge.nativeLastErrorCode() != SeekdbSqliteErrorMapper.SEEKDB_SUCCESS) {
                throw SeekdbSqliteErrorMapper.fromRc(
                        SeekdbNativeBridge.nativeLastErrorCode(),
                        "seekdb_stmt_execute failed");
            }
            outcome.affectedRows = SeekdbNativeBridge.nativeStmtAffectedRows(stmt);
            outcome.insertId = SeekdbNativeBridge.nativeStmtInsertId(stmt);
            if (result != 0L) {
                int columnCount = SeekdbNativeBridge.nativeResultColumnCount(result);
                if (columnCount > 0) {
                    outcome.columns = new String[columnCount];
                    for (int i = 0; i < columnCount; i++) {
                        String name = SeekdbNativeBridge.nativeResultColumnName(result, i);
                        outcome.columns[i] = name == null ? ("col_" + i) : name;
                    }
                } else {
                    outcome.columns = new String[0];
                }
                Object[][] rows =
                        cancellationSignal == null
                                ? SeekdbNativeBridge.nativeResultFetchAllTyped(result)
                                : SeekdbNativeBridge.nativeResultFetchAllTyped(result, cancellationSignal);
                outcome.rows = rows;
                if (rows != null && rows.length > 0 && rows[0] != null && rows[0].length > 0) {
                    outcome.firstValue = rows[0][0] == null ? null : String.valueOf(rows[0][0]);
                }
                SeekdbNativeBridge.nativeResultFree(result);
            }
            return outcome;
        } finally {
            SeekdbNativeBridge.nativeStmtClose(stmt);
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
                throw SeekdbSqliteErrorMapper.fromRc(rc, "seekdb_stmt_bind_value failed");
            }
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
