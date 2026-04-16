package com.oceanbase.seekdb.android.compat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.experimental.runners.Enclosed;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Enclosed.class)
public final class SeekdbCompatSqlTest {

    /** Row: expected output, SQL input, case name (shown in IDE / Gradle report). */
    @RunWith(Parameterized.class)
    public static final class NormalizeParameterizedTest {

        @Parameters(name = "{2}")
        public static Collection<Object[]> parameters() {
            return Arrays.asList(
                    new Object[] {
                        "REPLACE INTO t(a) VALUES (?)",
                        "INSERT OR REPLACE INTO t(a) VALUES (?)",
                        "insertOrReplace"
                    },
                    new Object[] {
                        // Prefix is rewritten; remainder keeps original casing.
                        "REPLACE INTO t(a) values (?)",
                        "  insert or replace into t(a) values (?)",
                        "insertOrReplace_leadingSpace_mixedCase"
                    },
                    new Object[] {
                        "INSERT IGNORE INTO t(a) VALUES (?)",
                        "INSERT OR IGNORE INTO t(a) VALUES (?)",
                        "insertOrIgnore"
                    },
                    new Object[] {
                        "INSERT INTO t(a) VALUES (?)",
                        "INSERT OR ABORT INTO t(a) VALUES (?)",
                        "insertOrAbort"
                    },
                    new Object[] {
                        "INSERT INTO `todo_table` (`id`,`title`) VALUES (?,?)",
                        "INSERT OR ABORT INTO `todo_table` (`id`,`title`) VALUES (nullif(?, 0),?)",
                        "insertOrAbort_roomNullIfZero"
                    },
                    new Object[] {
                        "UPDATE `todo_table` SET `id` = ? WHERE `id` = ?",
                        "UPDATE OR ABORT `todo_table` SET `id` = ? WHERE `id` = ?",
                        "updateOrAbort"
                    },
                    new Object[] {
                        "UPDATE IGNORE t SET a = 1 WHERE b = ?",
                        "UPDATE OR IGNORE t SET a = 1 WHERE b = ?",
                        "updateOrIgnore"
                    },
                    new Object[] {
                        "INSERT INTO t(a) VALUES (?)",
                        "INSERT OR FAIL INTO t(a) VALUES (?)",
                        "insertOrFail"
                    },
                    new Object[] {
                        "INSERT INTO t(a) VALUES (?)",
                        "INSERT OR ROLLBACK INTO t(a) VALUES (?)",
                        "insertOrRollback"
                    },
                    new Object[] {
                        "CREATE TABLE IF NOT EXISTS room_table_modification_log ("
                                + "table_id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                                + " invalidated BIGINT NOT NULL DEFAULT 0)",
                        "CREATE TEMP TABLE room_table_modification_log ("
                                + "table_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                                + " invalidated INTEGER NOT NULL DEFAULT 0)",
                        "createTempRoomModificationLog_fullDdl"
                    },
                    new Object[] {
                        "CREATE TABLE IF NOT EXISTS `todo_table` (`id` BIGINT PRIMARY KEY AUTO_INCREMENT "
                                + "NOT NULL, `title` TEXT)",
                        "CREATE TABLE IF NOT EXISTS `todo_table` (`id` INTEGER PRIMARY KEY AUTOINCREMENT "
                                + "NOT NULL, `title` TEXT)",
                        "roomStylePrimaryKey_autoincrement"
                    },
                    new Object[] {
                        "CREATE TABLE IF NOT EXISTS t (`createdAt` BIGINT NOT NULL, `n` BIGINT)",
                        "CREATE TABLE IF NOT EXISTS t (`createdAt` INTEGER NOT NULL, `n` INTEGER)",
                        "createTable_integerBecomesBigint"
                    },
                    new Object[] {
                        "CREATE TABLE IF NOT EXISTS room_table_modification_log (id INT)",
                        "CREATE TEMP TABLE `room_table_modification_log` (id INT)",
                        "createTempRoomModificationLog_quotedName"
                    },
                    new Object[] {
                        "CREATE TABLE IF NOT EXISTS room_table_modification_log (id INT)",
                        "CREATE TABLE room_table_modification_log (id INT)",
                        "createTableRoomModificationLog_withoutIfNotExists"
                    },
                    new Object[] {
                        "CREATE TABLE _tmp_foo (x INT)",
                        "CREATE TEMP TABLE _tmp_foo (x INT)",
                        "genericCreateTempTable_stripsTemp"
                    },
                    new Object[] {
                        "CREATE TRIGGER trg AFTER INSERT ON `items` FOR EACH ROW BEGIN UPDATE x; END",
                        "CREATE TEMP TRIGGER trg AFTER INSERT ON `items` FOR EACH ROW BEGIN UPDATE x; END",
                        "createTempTrigger_becomesCreateTrigger"
                    },
                    new Object[] {
                        "CREATE TRIGGER trg AFTER INSERT ON `items` "
                                + "FOR EACH ROW BEGIN UPDATE room_table_modification_log SET invalidated = 1; END",
                        "CREATE TRIGGER trg AFTER INSERT ON `items` "
                                + "BEGIN UPDATE room_table_modification_log SET invalidated = 1; END",
                        "trigger_insertsForEachRowBeforeBegin"
                    },
                    new Object[] {
                        "SELECT * FROM t WHERE id = ?",
                        "SELECT * FROM t WHERE id = ?",
                        "unrecognized_unchanged"
                    });
        }

        private final String expected;
        private final String input;

        public NormalizeParameterizedTest(String expected, String input, String name) {
            this.expected = expected;
            this.input = input;
        }

        @Test
        public void normalize_matchesExpected() {
            assertEquals(expected, SeekdbCompatSql.normalize(input));
        }
    }

    public static final class DiagnosticsTest {

        @Test
        public void formatPrepareFailure_includesPhaseSqlAndCode() {
            String d =
                    SeekdbCompatDiagnostics.formatPrepareFailure(
                            "INSERT INTO `t` (`id`) VALUES (?)", -3);
            assertTrue(d.contains("stmt_prepare"));
            assertTrue(d.contains("lastErrCode=-3"));
            assertTrue(d.contains("INSERT INTO"));
        }
    }

    public static final class NormalizeEdgeCasesTest {

        @Test
        public void null_returnsNull() {
            assertNull(SeekdbCompatSql.normalize(null));
        }

        @Test
        public void roomInsertLeadingIdColumn_detection() {
            assertTrue(
                    SeekdbCompatSql.isRoomInsertLeadingBacktickIdColumn(
                            "INSERT INTO `todo_table` (`id`,`title`) VALUES (?,?)"));
            assertTrue(
                    SeekdbCompatSql.isRoomInsertLeadingBacktickIdColumn(
                            "INSERT IGNORE INTO t (`id`,a) VALUES (?,?)"));
            assertFalse(
                    SeekdbCompatSql.isRoomInsertLeadingBacktickIdColumn(
                            "INSERT INTO `todo_table` (`title`,`id`) VALUES (?,?)"));
            assertFalse(
                    SeekdbCompatSql.isRoomInsertLeadingBacktickIdColumn(
                            "UPDATE `todo_table` SET `id`=?"));
        }
    }
}
