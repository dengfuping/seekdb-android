package com.oceanbase.seekdb.android.compat;

import java.util.Locale;

/**
 * Appends SQL snippet and numeric codes to thrown {@link android.database.sqlite.SQLiteException}
 * messages when native errors are terse.
 */
final class SeekdbCompatDiagnostics {
    private static final int SQL_SNIP_MAX = 900;

    private SeekdbCompatDiagnostics() {}

    static String formatStmtExecuteFailure(
            long connectionPtr,
            String sql,
            int stmtExecRc,
            int lastErrCode,
            int resolvedRc,
            int errno) {
        return String.format(
                Locale.ROOT,
                " | seekdbAndroidDiag phase=stmt_execute connPtr=%d resolvedRc=%d stmtExecRc=%d"
                        + " lastErrCode=%d errno=%d sql=\"%s\"",
                connectionPtr,
                resolvedRc,
                stmtExecRc,
                lastErrCode,
                errno,
                escapeSqlSnippet(sql));
    }

    static String formatPrepareFailure(String sql, int lastErrCode) {
        return String.format(
                Locale.ROOT,
                " | seekdbAndroidDiag phase=stmt_prepare lastErrCode=%d sql=\"%s\"",
                lastErrCode,
                escapeSqlSnippet(sql));
    }

    static String formatBindFailure(String sql, int paramIndex1Based, int bindRc) {
        return String.format(
                Locale.ROOT,
                " | seekdbAndroidDiag phase=stmt_bind paramIndex=%d bindRc=%d sql=\"%s\"",
                paramIndex1Based,
                bindRc,
                escapeSqlSnippet(sql));
    }

    private static String escapeSqlSnippet(String sql) {
        if (sql == null) {
            return "";
        }
        String oneLine = sql.replace('\r', ' ').replace('\n', ' ');
        if (oneLine.length() <= SQL_SNIP_MAX) {
            return oneLine.replace('"', '\'');
        }
        return oneLine.substring(0, SQL_SNIP_MAX).replace('"', '\'') + "...";
    }
}
