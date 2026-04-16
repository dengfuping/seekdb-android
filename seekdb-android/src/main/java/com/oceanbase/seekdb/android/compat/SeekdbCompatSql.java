package com.oceanbase.seekdb.android.compat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites a subset of SQLite statement prefixes to MySQL/OceanBase shapes expected by the
 * embedded engine (Room emits SQLite {@code INSERT OR …}, {@code UPDATE OR …}, {@code
 * AUTOINCREMENT}, and {@code nullif(?, 0)} for autoincrement keys).
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
    private static final Pattern UPDATE_OR_IGNORE =
            Pattern.compile("(?is)^\\s*UPDATE\\s+OR\\s+IGNORE\\s+");
    private static final Pattern UPDATE_OR_ABORT =
            Pattern.compile("(?is)^\\s*UPDATE\\s+OR\\s+ABORT\\s+");
    private static final Pattern UPDATE_OR_FAIL =
            Pattern.compile("(?is)^\\s*UPDATE\\s+OR\\s+FAIL\\s+");
    private static final Pattern UPDATE_OR_ROLLBACK =
            Pattern.compile("(?is)^\\s*UPDATE\\s+OR\\s+ROLLBACK\\s+");
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
    /** Room / SQLite DDL; embed engine expects MySQL {@code AUTO_INCREMENT}. */
    private static final Pattern SQLITE_AUTOINCREMENT = Pattern.compile("(?is)\\bAUTOINCREMENT\\b");
    /**
     * SQLite {@code INTEGER} has 64-bit integer affinity; MySQL/OceanBase {@code INTEGER} is 32-bit.
     * Room {@code long} columns (e.g. epoch millis) need {@code BIGINT} on embedded engine.
     */
    private static final Pattern SQLITE_INTEGER_TYPE = Pattern.compile("(?is)\\bINTEGER\\b");
    /**
     * Room's generated {@code INSERT} uses {@code nullif(?,0)} so bind {@code 0} becomes SQL NULL
     * for SQLite autoincrement; MySQL/OceanBase treat literal {@code 0} on an {@code AUTO_INCREMENT}
     * column like the next value when {@code NO_AUTO_VALUE_ON_ZERO} is off (typical). The embed
     * parser may not implement {@code NULLIF} here.
     */
    private static final Pattern ROOM_NULLIF_BIND_ZERO =
            Pattern.compile("(?is)nullif\\s*\\(\\s*\\?\\s*,\\s*0\\s*\\)");
    /**
     * Room lists {@code `id`} first for {@code @PrimaryKey(autoGenerate = true)} inserts; bind {@code 0}
     * means auto-assign. Embed may reject literal {@code 0} on {@code AUTO_INCREMENT}; {@link
     * SeekdbCompatStatement} then binds SQL NULL for parameter 1.
     */
    private static final Pattern ROOM_INSERT_LEADING_BACKTICK_ID =
            Pattern.compile(
                    "(?is)^\\s*INSERT\\s+(?:IGNORE\\s+)?INTO\\s+(?:`[^`]+`|\\w+)\\s*\\(\\s*`id`\\s*,");

    private SeekdbCompatSql() {}

    static boolean isRoomInsertLeadingBacktickIdColumn(String sql) {
        return sql != null && ROOM_INSERT_LEADING_BACKTICK_ID.matcher(sql).find();
    }

    static String normalize(String sql) {
        if (sql == null) {
            return null;
        }
        Matcher m;
        if ((m = CREATE_TEMP_TRIGGER.matcher(sql)).find()) {
            sql = m.replaceFirst("CREATE TRIGGER ");
        }
        if ((m = UPDATE_OR_IGNORE.matcher(sql)).find()) {
            sql = m.replaceFirst("UPDATE IGNORE ");
        }
        if ((m = UPDATE_OR_ABORT.matcher(sql)).find()) {
            sql = m.replaceFirst("UPDATE ");
        }
        if ((m = UPDATE_OR_FAIL.matcher(sql)).find()) {
            sql = m.replaceFirst("UPDATE ");
        }
        if ((m = UPDATE_OR_ROLLBACK.matcher(sql)).find()) {
            sql = m.replaceFirst("UPDATE ");
        }
        if ((m = CREATE_TEMP_ROOM_MODIFICATION_LOG.matcher(sql)).find()) {
            return finishEmbeddedRewrites(
                    m.replaceFirst("CREATE TABLE IF NOT EXISTS room_table_modification_log ("));
        }
        if ((m = CREATE_TEMP_TABLE.matcher(sql)).find()) {
            sql = m.replaceFirst("CREATE TABLE ");
        }
        if ((m = CREATE_ROOM_MODIFICATION_LOG_TABLE.matcher(sql)).find()) {
            return finishEmbeddedRewrites(
                    m.replaceFirst("CREATE TABLE IF NOT EXISTS room_table_modification_log ("));
        }
        if ((m = INSERT_OR_REPLACE.matcher(sql)).find()) {
            return finishEmbeddedRewrites(m.replaceFirst("REPLACE INTO "));
        }
        if ((m = INSERT_OR_IGNORE.matcher(sql)).find()) {
            return finishEmbeddedRewrites(m.replaceFirst("INSERT IGNORE INTO "));
        }
        if ((m = INSERT_OR_ABORT.matcher(sql)).find()) {
            return finishEmbeddedRewrites(m.replaceFirst("INSERT INTO "));
        }
        if ((m = INSERT_OR_FAIL.matcher(sql)).find()) {
            return finishEmbeddedRewrites(m.replaceFirst("INSERT INTO "));
        }
        if ((m = INSERT_OR_ROLLBACK.matcher(sql)).find()) {
            return finishEmbeddedRewrites(m.replaceFirst("INSERT INTO "));
        }
        if ((m = SQLITE_TRIGGER_OMIT_FOR_EACH_ROW.matcher(sql)).find()) {
            sql = m.replaceFirst("$1FOR EACH ROW $2");
        }
        return finishEmbeddedRewrites(sql);
    }

    private static String finishEmbeddedRewrites(String sql) {
        sql = SQLITE_AUTOINCREMENT.matcher(sql).replaceAll("AUTO_INCREMENT");
        sql = ROOM_NULLIF_BIND_ZERO.matcher(sql).replaceAll("?");
        sql = rewriteCreateTableIntegerType(sql);
        return sql;
    }

    private static String rewriteCreateTableIntegerType(String sql) {
        if (sql == null) {
            return null;
        }
        String t = sql.trim();
        if (!(t.length() >= 12 && t.regionMatches(true, 0, "CREATE TABLE", 0, 12))) {
            return sql;
        }
        return SQLITE_INTEGER_TYPE.matcher(sql).replaceAll("BIGINT");
    }
}
