package com.oceanbase.seekdb.android.compat;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** JVM unit tests: contract for App Inspection schema SQL (no native required). */
public final class SeekdbInspectorSchemaQueryTest {

    @Test
    public void forAppInspectionGetSchema_targetsInformationSchemaAndExpectedAliases() {
        String sql = SeekdbInspectorSchemaQuery.FOR_APP_INSPECTION_GET_SCHEMA;
        String u = sql.toUpperCase(java.util.Locale.ROOT);
        assertTrue(u.contains("INFORMATION_SCHEMA.TABLES"));
        assertTrue(u.contains("INFORMATION_SCHEMA.COLUMNS"));
        assertTrue(u.contains("TABLE_SCHEMA = DATABASE()"));
        assertTrue(sql.contains("AS type"));
        assertTrue(sql.contains("AS tableName"));
        assertTrue(sql.contains("AS columnName"));
        assertTrue(sql.contains("AS columnType"));
        assertTrue(sql.contains("AS notnull"));
        assertTrue(sql.contains("AS pk"));
        assertTrue(sql.contains("`unique`"));
    }
}
