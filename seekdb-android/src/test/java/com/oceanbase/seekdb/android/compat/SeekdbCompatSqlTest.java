package com.oceanbase.seekdb.android.compat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
                                + "table_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                                + " invalidated INTEGER NOT NULL DEFAULT 0)",
                        "CREATE TEMP TABLE room_table_modification_log ("
                                + "table_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                                + " invalidated INTEGER NOT NULL DEFAULT 0)",
                        "createTempRoomModificationLog_fullDdl"
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

    public static final class NormalizeEdgeCasesTest {

        @Test
        public void null_returnsNull() {
            assertNull(SeekdbCompatSql.normalize(null));
        }
    }
}
