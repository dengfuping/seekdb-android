package com.oceanbase.seekdb.android.compat;

import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteFullException;
import android.database.sqlite.SQLiteMisuseException;
import com.oceanbase.seekdb.android.core.SeekdbError;
import com.oceanbase.seekdb.android.core.SeekdbNativeBridge;
import java.util.Locale;

final class SeekdbSqliteErrorMapper {
    static final int SEEKDB_SUCCESS = 0;
    static final int SEEKDB_ERROR_INVALID_PARAM = -1;
    static final int SEEKDB_ERROR_CONNECTION_FAILED = -2;
    static final int SEEKDB_ERROR_QUERY_FAILED = -3;
    static final int SEEKDB_ERROR_MEMORY_ALLOC = -4;
    static final int SEEKDB_ERROR_NOT_INITIALIZED = -5;

    private SeekdbSqliteErrorMapper() {}

    static SQLiteException fromRc(int rc, String fallbackMessage) {
        String message = safeNativeLastErrorMessage();
        String sqlState = safeNativeLastSqlState();
        if (message == null || message.isEmpty()) {
            message = fallbackMessage;
        }
        return fromRc(rc, message, sqlState);
    }

    static SQLiteException fromRc(int rc, String message, String sqlState) {
        if (rc == SEEKDB_ERROR_INVALID_PARAM) {
            return new SQLiteMisuseException(message);
        }
        if (rc == SEEKDB_ERROR_MEMORY_ALLOC) {
            return new SQLiteFullException(message);
        }
        if (rc == SEEKDB_ERROR_QUERY_FAILED) {
            return classifyQueryFailure(message, sqlState);
        }
        if (rc == SEEKDB_ERROR_CONNECTION_FAILED || rc == SEEKDB_ERROR_NOT_INITIALIZED) {
            return new SQLiteException(message);
        }
        if (looksLikeCorruption(message, sqlState)) {
            return new SQLiteDatabaseCorruptException(message);
        }
        return new SQLiteException(message);
    }

    static SQLiteException classifyQueryFailure(String message, String sqlState) {
        if (sqlState != null && sqlState.length() >= 2 && sqlState.startsWith("23")) {
            return new SQLiteConstraintException(message);
        }
        if (message != null) {
            String upper = message.toUpperCase(Locale.ROOT);
            if (upper.contains("UNIQUE")
                    || upper.contains("FOREIGN KEY")
                    || upper.contains("CHECK ")
                    || upper.contains("CHECK:")
                    || upper.contains("NOT NULL")
                    || upper.contains("CONSTRAINT")) {
                return new SQLiteConstraintException(message);
            }
        }
        return new SQLiteException(message);
    }

    private static boolean looksLikeCorruption(String message, String sqlState) {
        if (sqlState != null && (sqlState.startsWith("XX") || sqlState.startsWith("HY"))) {
            return true;
        }
        if (message == null) {
            return false;
        }
        String upper = message.toUpperCase(Locale.ROOT);
        return upper.contains("CORRUPT") || upper.contains("MALFORMED") || upper.contains("NOT A DATABASE");
    }

    static void throwIfError(int rc, String fallbackMessage) {
        if (rc != SEEKDB_SUCCESS) {
            throw fromRc(rc, fallbackMessage);
        }
    }

    static SeekdbError lastErrorSnapshot() {
        return new SeekdbError(
                safeNativeLastErrorCode(),
                safeNativeLastErrorMessage(),
                safeNativeLastSqlState());
    }

    private static int safeNativeLastErrorCode() {
        try {
            return SeekdbNativeBridge.nativeLastErrorCode();
        } catch (Throwable ignored) {
            return SEEKDB_SUCCESS;
        }
    }

    private static String safeNativeLastErrorMessage() {
        try {
            return SeekdbNativeBridge.nativeLastErrorMessage();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String safeNativeLastSqlState() {
        try {
            String s = SeekdbNativeBridge.nativeLastSqlState();
            return s == null || s.isEmpty() ? null : s;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
