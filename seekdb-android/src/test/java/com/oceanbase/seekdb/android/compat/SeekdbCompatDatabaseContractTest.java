package com.oceanbase.seekdb.android.compat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.util.Pair;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.Test;

public class SeekdbCompatDatabaseContractTest {
    @Test
    public void needUpgrade_followsVersionComparison() {
        SeekdbCompatDatabase db = new SeekdbCompatDatabase("unit.db");
        db.setVersion(2);
        assertTrue(db.needUpgrade(3));
        assertFalse(db.needUpgrade(2));
        assertFalse(db.needUpgrade(1));
    }

    @Test
    public void walFlags_toggleCorrectly() {
        SeekdbCompatDatabase db = new SeekdbCompatDatabase("unit.db");
        assertFalse(db.isWriteAheadLoggingEnabled());
        db.enableWriteAheadLogging();
        assertTrue(db.isWriteAheadLoggingEnabled());
        db.disableWriteAheadLogging();
        assertFalse(db.isWriteAheadLoggingEnabled());
    }

    @Test
    public void getAttachedDbs_returnsEmptyList_notNull() {
        SeekdbCompatDatabase db = new SeekdbCompatDatabase("unit.db");
        List<Pair<String, String>> attached = db.getAttachedDbs();
        assertNotNull(attached);
        assertTrue(attached.isEmpty());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getMaximumSize_throwsUnsupported() {
        SeekdbCompatDatabase db = new SeekdbCompatDatabase("unit.db");
        db.getMaximumSize();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void setMaximumSize_throwsUnsupported() {
        SeekdbCompatDatabase db = new SeekdbCompatDatabase("unit.db");
        db.setMaximumSize(1024L);
    }

    @Test
    public void nestedBegin_throwsIllegalState() throws Exception {
        SeekdbCompatDatabase db = new SeekdbCompatDatabase("unit.db");
        Field depthField = SeekdbCompatDatabase.class.getDeclaredField("transactionDepth");
        depthField.setAccessible(true);
        depthField.setInt(db, 1);
        try {
            try {
                db.beginTransaction();
                throw new AssertionError("expected IllegalStateException");
            } catch (IllegalStateException expected) {
                assertEquals(
                        "Nested transactions are not supported; finish the current transaction first.",
                        expected.getMessage());
            }
        } finally {
            depthField.setInt(db, 0);
        }
    }

    @Test
    public void endWithoutBegin_throwsIllegalState() {
        SeekdbCompatDatabase db = new SeekdbCompatDatabase("unit.db");
        try {
            db.endTransaction();
        } catch (IllegalStateException expected) {
            assertEquals("endTransaction called but no transaction is active", expected.getMessage());
            return;
        }
        throw new AssertionError("expected IllegalStateException");
    }

    @Test
    public void setTransactionSuccessful_withoutBegin_throwsIllegalState() {
        SeekdbCompatDatabase db = new SeekdbCompatDatabase("unit.db");
        try {
            db.setTransactionSuccessful();
        } catch (IllegalStateException expected) {
            assertEquals(
                    "setTransactionSuccessful called but no transaction is active", expected.getMessage());
            return;
        }
        throw new AssertionError("expected IllegalStateException");
    }
}
