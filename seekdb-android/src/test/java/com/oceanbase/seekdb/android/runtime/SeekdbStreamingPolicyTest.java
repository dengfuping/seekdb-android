package com.oceanbase.seekdb.android.runtime;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.oceanbase.seekdb.android.sqlite.SeekdbSQLite;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SeekdbStreamingPolicyTest {

    @Before
    @After
    public void resetPolicy() {
        SeekdbStreamingPolicy.setUseStreamingQueryCursors(false);
    }

    @Test
    public void defaultStreamingQueryCursorsDisabled() {
        assertFalse(SeekdbStreamingPolicy.useStreamingQueryCursors());
        assertFalse(SeekdbSQLite.isStreamingQueryCursorsEnabled());
    }

    @Test
    public void seekdbSQLiteDelegatesToPolicy() {
        SeekdbSQLite.setStreamingQueryCursorsEnabled(true);
        assertTrue(SeekdbStreamingPolicy.useStreamingQueryCursors());
        assertTrue(SeekdbSQLite.isStreamingQueryCursorsEnabled());
        SeekdbSQLite.setStreamingQueryCursorsEnabled(false);
        assertFalse(SeekdbStreamingPolicy.useStreamingQueryCursors());
    }
}
