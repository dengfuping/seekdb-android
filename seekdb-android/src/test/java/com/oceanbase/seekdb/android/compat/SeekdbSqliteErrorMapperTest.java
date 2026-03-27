package com.oceanbase.seekdb.android.compat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteFullException;
import android.database.sqlite.SQLiteMisuseException;
import org.junit.Test;

public class SeekdbSqliteErrorMapperTest {
    @Test
    public void invalidParam_mapsToMisuse() {
        SQLiteException e =
                SeekdbSqliteErrorMapper.fromRc(
                        SeekdbSqliteErrorMapper.SEEKDB_ERROR_INVALID_PARAM,
                        "bad arg");
        assertTrue(e instanceof SQLiteMisuseException);
    }

    @Test
    public void memoryAlloc_mapsToFull() {
        SQLiteException e =
                SeekdbSqliteErrorMapper.fromRc(
                        SeekdbSqliteErrorMapper.SEEKDB_ERROR_MEMORY_ALLOC,
                        "oom");
        assertTrue(e instanceof SQLiteFullException);
    }

    @Test
    public void queryFailed_generic_mapsToSqliteException() {
        SQLiteException e =
                SeekdbSqliteErrorMapper.classifyQueryFailure("network timeout", null);
        assertTrue(e instanceof SQLiteException);
        assertFalse(e instanceof SQLiteConstraintException);
    }

    @Test
    public void queryFailed_uniqueKeyword_mapsToConstraint() {
        SQLiteException e =
                SeekdbSqliteErrorMapper.classifyQueryFailure(
                        "UNIQUE constraint failed: t.x", null);
        assertTrue(e instanceof SQLiteConstraintException);
    }

    @Test
    public void queryFailed_sqlState23_mapsToConstraint() {
        SQLiteException e =
                SeekdbSqliteErrorMapper.classifyQueryFailure("error", "23505");
        assertTrue(e instanceof SQLiteConstraintException);
    }
}
