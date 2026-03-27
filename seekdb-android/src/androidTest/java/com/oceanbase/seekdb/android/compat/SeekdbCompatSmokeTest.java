package com.oceanbase.seekdb.android.compat;

import static org.junit.Assert.assertNotNull;
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
public class SeekdbCompatSmokeTest {
    @Test
    public void openHelper_shouldProvideDatabaseInstance() {
        Assume.assumeTrue("Skip when libseekdb.so is unavailable", SeekdbClient.isNativeAvailable());
        Context context = ApplicationProvider.getApplicationContext();
        SupportSQLiteOpenHelper.Configuration configuration =
                SupportSQLiteOpenHelper.Configuration.builder(context)
                        .name("seekdb_compat_smoke.db")
                        .callback(new SupportSQLiteOpenHelper.Callback(1) {
                            @Override
                            public void onCreate(SupportSQLiteDatabase db) {
                                db.execSQL("CREATE TABLE IF NOT EXISTS smoke(id INTEGER PRIMARY KEY, name TEXT)");
                            }

                            @Override
                            public void onUpgrade(
                                    SupportSQLiteDatabase db, int oldVersion, int newVersion) {}
                        })
                        .build();

        SupportSQLiteOpenHelper helper = new SeekdbOpenHelperFactory().create(configuration);
        SupportSQLiteDatabase db = helper.getWritableDatabase();
        assertNotNull(db);
        assertTrue(db.isOpen());
        helper.close();
    }
}
