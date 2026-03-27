package com.oceanbase.seekdb.android.nativeapi;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import com.oceanbase.seekdb.android.runtime.SeekdbConnectionPool;
import org.junit.Test;

public class SeekdbConnectionPoolTest {
    @Test
    public void acquire_withoutInstall_throws() {
        SeekdbConnectionPool pool = new SeekdbConnectionPool();
        try {
            pool.acquire(true);
            fail();
        } catch (IllegalStateException expected) {
            // expected
        }
    }

    @Test
    public void acquire_afterInstall_returnsHandle() {
        SeekdbConnectionPool pool = new SeekdbConnectionPool();
        SeekdbConnection c = new SeekdbConnection(0L);
        pool.installPrimary(c);
        SeekdbConnectionPool.PooledConnection h = pool.acquire(false);
        assertSame(c, h.connection());
        pool.release(h);
    }
}
