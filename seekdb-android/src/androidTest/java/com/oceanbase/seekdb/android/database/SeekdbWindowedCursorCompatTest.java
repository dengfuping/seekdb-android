package com.oceanbase.seekdb.android.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import androidx.sqlite.db.SimpleSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.oceanbase.seekdb.android.compat.SeekdbOpenHelperFactory;
import com.oceanbase.seekdb.android.nativeapi.SeekdbClient;
import com.oceanbase.seekdb.android.runtime.SeekdbStreamingPolicy;
import com.oceanbase.seekdb.android.sqlite.SeekdbSQLite;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SeekdbWindowedCursorCompatTest {

    @Before
    public void assumeNative() {
        Assume.assumeTrue("Skip when libseekdb.so is unavailable", SeekdbClient.isNativeAvailable());
        SeekdbSQLite.setStreamingQueryCursorsEnabled(true);
    }

    @After
    public void resetStreaming() {
        SeekdbStreamingPolicy.setUseStreamingQueryCursors(false);
    }

    @Test
    public void windowedQuery_hasWindow_randomAccessWithinBufferedRange() {
        org.junit.Assert.assertTrue(
                "streaming must be on for this test",
                SeekdbSQLite.isStreamingQueryCursorsEnabled());
        Context context = ApplicationProvider.getApplicationContext();
        SupportSQLiteOpenHelper.Configuration configuration =
                SupportSQLiteOpenHelper.Configuration.builder(context)
                        .name("seekdb_windowed_cursor.db")
                        .callback(
                                new SupportSQLiteOpenHelper.Callback(1) {
                                    @Override
                                    public void onCreate(SupportSQLiteDatabase db) {
                                        db.execSQL(
                                                "CREATE TABLE IF NOT EXISTS t_n (id INTEGER PRIMARY KEY, v TEXT)");
                                    }

                                    @Override
                                    public void onUpgrade(
                                            SupportSQLiteDatabase db, int oldVersion, int newVersion) {}
                                })
                        .build();
        SupportSQLiteOpenHelper helper = new SeekdbOpenHelperFactory().create(configuration);
        SupportSQLiteDatabase db = helper.getWritableDatabase();
        try {
            db.execSQL("DELETE FROM t_n");
            for (int i = 1; i <= 5; i++) {
                ContentValues cv = new ContentValues();
                cv.put("id", i);
                cv.put("v", "r" + i);
                db.insert("t_n", 0, cv);
            }

            Cursor c =
                    db.query(
                            new SimpleSQLiteQuery(
                                    "SELECT v FROM t_n ORDER BY id", new Object[] {}));
            try {
                assertTrue(
                        "expected SeekdbWindowedCursor, got " + c.getClass().getName(),
                        c instanceof SeekdbWindowedCursor);
                SeekdbWindowedCursor wc = (SeekdbWindowedCursor) c;
                // AbstractWindowedCursor allocates mWindow on first onMove; hasWindow() is false until then.
                assertTrue("moveToFirst", c.moveToFirst());
                assertTrue("hasWindow after first fill", wc.hasWindow());

                assertTrue("moveToLast", c.moveToLast());
                assertEquals("r5", c.getString(0));
                assertTrue("moveToFirst", c.moveToFirst());
                assertEquals("r1", c.getString(0));
                assertEquals("row count", 5, c.getCount());
                assertTrue("moveToPosition(2)", c.moveToPosition(2));
                assertEquals("r3", c.getString(0));
            } finally {
                c.close();
            }
        } finally {
            helper.close();
        }
    }
}
