package com.oceanbase.seekdb.android.compat;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.oceanbase.seekdb.android.nativeapi.SeekdbClient;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SeekdbCompatMigrationTest {
    @Test
    public void openHelperUpgradeCallback_shouldBeInvoked() {
        Assume.assumeTrue("Skip when libseekdb.so is unavailable", SeekdbClient.isNativeAvailable());
        Context context = ApplicationProvider.getApplicationContext();
        String dbName = "seekdb_migration_case.db";
        AtomicBoolean upgraded = new AtomicBoolean(false);

        SupportSQLiteOpenHelper helperV1 =
                new SeekdbOpenHelperFactory()
                        .create(SupportSQLiteOpenHelper.Configuration.builder(context)
                                .name(dbName)
                                .callback(new SupportSQLiteOpenHelper.Callback(1) {
                                    @Override
                                    public void onCreate(SupportSQLiteDatabase db) {
                                        db.execSQL(
                                                "CREATE TABLE IF NOT EXISTS migrate_t(id INTEGER PRIMARY KEY, v TEXT)");
                                    }

                                    @Override
                                    public void onUpgrade(
                                            SupportSQLiteDatabase db, int oldVersion, int newVersion) {}
                                })
                                .build());
        helperV1.getWritableDatabase();
        helperV1.close();

        SupportSQLiteOpenHelper helperV2 =
                new SeekdbOpenHelperFactory()
                        .create(SupportSQLiteOpenHelper.Configuration.builder(context)
                                .name(dbName)
                                .callback(new SupportSQLiteOpenHelper.Callback(2) {
                                    @Override
                                    public void onCreate(SupportSQLiteDatabase db) {}

                                    @Override
                                    public void onUpgrade(
                                            SupportSQLiteDatabase db, int oldVersion, int newVersion) {
                                        upgraded.set(true);
                                    }
                                })
                                .build());
        helperV2.getWritableDatabase();
        helperV2.close();

        assertTrue(upgraded.get());
    }
}
