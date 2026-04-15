package com.oceanbase.seekdb.android.compat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites a subset of SQLite statement prefixes to MySQL/OceanBase shapes expected by the
 * embedded engine (Room emits SQLite {@code INSERT OR …} forms).
 */
final class SeekdbCompatSql {
    private static final Pattern INSERT_OR_REPLACE =
            Pattern.compile("(?is)^\\s*INSERT\\s+OR\\s+REPLACE\\s+INTO\\s+");
    private static final Pattern INSERT_OR_IGNORE =
            Pattern.compile("(?is)^\\s*INSERT\\s+OR\\s+IGNORE\\s+INTO\\s+");
    private static final Pattern INSERT_OR_ABORT =
            Pattern.compile("(?is)^\\s*INSERT\\s+OR\\s+ABORT\\s+INTO\\s+");
    private static final Pattern INSERT_OR_FAIL =
            Pattern.compile("(?is)^\\s*INSERT\\s+OR\\s+FAIL\\s+INTO\\s+");
    private static final Pattern INSERT_OR_ROLLBACK =
            Pattern.compile("(?is)^\\s*INSERT\\s+OR\\s+ROLLBACK\\s+INTO\\s+");
    /**
     * Room {@code InvalidationTracker} DDL — must run before generic {@link #CREATE_TEMP_TABLE} so we
     * never emit a plain {@code CREATE TABLE} without {@code IF NOT EXISTS} (otherwise reopen can hit
     * {@code Table already exists}).
     */
    private static final Pattern CREATE_TEMP_ROOM_MODIFICATION_LOG =
            Pattern.compile(
                    "(?is)^\\s*CREATE\\s+TEMP\\s+TABLE\\s+[`']?room_table_modification_log[`']?\\s*\\(");
    /** SQLite {@code TEMP TABLE}; embed has no temp tables — use a regular table (Room invalidation log). */
    private static final Pattern CREATE_TEMP_TABLE =
            Pattern.compile("(?is)^\\s*CREATE\\s+TEMP\\s+TABLE\\s+");
    /** After temp→plain rewrite, or if engine ever sees this shape without TEMP. */
    private static final Pattern CREATE_ROOM_MODIFICATION_LOG_TABLE =
            Pattern.compile(
                    "(?is)^\\s*CREATE\\s+TABLE\\s+(?!IF\\s+NOT\\s+EXISTS\\s)[`']?room_table_modification_log[`']?\\s*\\(");
    /** Room invalidation uses {@code CREATE TEMP TRIGGER}. */
    private static final Pattern CREATE_TEMP_TRIGGER =
            Pattern.compile("(?is)^\\s*CREATE\\s+TEMP\\s+TRIGGER\\s+");
    /**
     * SQLite trigger bodies may omit {@code FOR EACH ROW}; MySQL/OceanBase requires it before
     * {@code BEGIN} (Room single-statement triggers).
     */
    private static final Pattern SQLITE_TRIGGER_OMIT_FOR_EACH_ROW =
            Pattern.compile(
                    "(?is)(AFTER\\s+(?:INSERT|UPDATE|DELETE)\\s+ON\\s+`[^`]+`\\s+)"
                            + "(?!FOR\\s+EACH\\s+ROW\\s+)(BEGIN\\s+)");

    private SeekdbCompatSql() {}

    static String normalize(String sql) {
        if (sql == null) {
            return null;
        }
        Matcher m;
        if ((m = CREATE_TEMP_TRIGGER.matcher(sql)).find()) {
            sql = m.replaceFirst("CREATE TRIGGER ");
        }
        if ((m = CREATE_TEMP_ROOM_MODIFICATION_LOG.matcher(sql)).find()) {
            return m.replaceFirst("CREATE TABLE IF NOT EXISTS room_table_modification_log (");
        }
        if ((m = CREATE_TEMP_TABLE.matcher(sql)).find()) {
            sql = m.replaceFirst("CREATE TABLE ");
        }
        if ((m = CREATE_ROOM_MODIFICATION_LOG_TABLE.matcher(sql)).find()) {
            return m.replaceFirst("CREATE TABLE IF NOT EXISTS room_table_modification_log (");
        }
        if ((m = INSERT_OR_REPLACE.matcher(sql)).find()) {
            return m.replaceFirst("REPLACE INTO ");
        }
        if ((m = INSERT_OR_IGNORE.matcher(sql)).find()) {
            return m.replaceFirst("INSERT IGNORE INTO ");
        }
        if ((m = INSERT_OR_ABORT.matcher(sql)).find()) {
            return m.replaceFirst("INSERT INTO ");
        }
        if ((m = INSERT_OR_FAIL.matcher(sql)).find()) {
            return m.replaceFirst("INSERT INTO ");
        }
        if ((m = INSERT_OR_ROLLBACK.matcher(sql)).find()) {
            return m.replaceFirst("INSERT INTO ");
        }
        if ((m = SQLITE_TRIGGER_OMIT_FOR_EACH_ROW.matcher(sql)).find()) {
            sql = m.replaceFirst("$1FOR EACH ROW $2");
        }
        return sql;
    }
}
