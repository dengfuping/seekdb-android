package com.oceanbase.seekdb.android.compat;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.oceanbase.seekdb.android.nativeapi.SeekdbClient;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SeekdbCompatPerformanceSmokeTest {
    @Test
    public void batchInsert_andSimpleQuery_shouldFinishWithinBudget() {
        Assume.assumeTrue("Skip when libseekdb.so is unavailable", SeekdbClient.isNativeAvailable());
        Context context = ApplicationProvider.getApplicationContext();
        SupportSQLiteOpenHelper helper =
                new SeekdbOpenHelperFactory()
                        .create(SupportSQLiteOpenHelper.Configuration.builder(context)
                                .name("seekdb_perf_smoke.db")
                                .callback(new SupportSQLiteOpenHelper.Callback(1) {
                                    @Override
                                    public void onCreate(SupportSQLiteDatabase db) {
                                        db.execSQL(
                                                "CREATE TABLE IF NOT EXISTS perf_t(id INTEGER PRIMARY KEY, v TEXT)");
                                    }

                                    @Override
                                    public void onUpgrade(
                                            SupportSQLiteDatabase db, int oldVersion, int newVersion) {}
                                })
                                .build());
        SupportSQLiteDatabase db = helper.getWritableDatabase();

        long start = System.currentTimeMillis();
        db.beginTransaction();
        try {
            for (int i = 0; i < 200; i++) {
                db.execSQL("INSERT INTO perf_t(id,v) VALUES(?,?)", new Object[]{i, "v_" + i});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        db.query("SELECT COUNT(*) FROM perf_t").close();
        long elapsed = System.currentTimeMillis() - start;
        helper.close();

        // Baseline smoke budget, tune in future benchmark phase.
        assertTrue("Performance smoke exceeded budget: " + elapsed, elapsed < 15000);
    }
}
