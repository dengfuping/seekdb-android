package com.oceanbase.seekdb.android.compat;

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
import com.oceanbase.seekdb.android.nativeapi.SeekdbClient;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SeekdbCompatCrudTest {
    private SupportSQLiteOpenHelper helper;
    private SupportSQLiteDatabase db;

    @Before
    public void setUp() {
        Assume.assumeTrue("Skip when libseekdb.so is unavailable", SeekdbClient.isNativeAvailable());
        Context context = ApplicationProvider.getApplicationContext();
        SupportSQLiteOpenHelper.Configuration configuration =
                SupportSQLiteOpenHelper.Configuration.builder(context)
                        .name("seekdb_compat_crud.db")
                        .callback(new SupportSQLiteOpenHelper.Callback(1) {
                            @Override
                            public void onCreate(SupportSQLiteDatabase db) {
                                db.execSQL("CREATE TABLE IF NOT EXISTS t_user(id INTEGER PRIMARY KEY, name TEXT)");
                            }

                            @Override
                            public void onUpgrade(
                                    SupportSQLiteDatabase db, int oldVersion, int newVersion) {}
                        })
                        .build();
        helper = new SeekdbOpenHelperFactory().create(configuration);
        db = helper.getWritableDatabase();
    }

    @After
    public void tearDown() {
        if (helper != null) {
            helper.close();
        }
    }

    @Test
    public void insertAndQuery_shouldWork() {
        ContentValues values = new ContentValues();
        values.put("id", 1);
        values.put("name", "alice");
        long id = db.insert("t_user", 0, values);
        assertTrue(id >= -1);

        Cursor cursor = db.query(new SimpleSQLiteQuery("SELECT name FROM t_user WHERE id = ?", new Object[]{1}));
        try {
            assertTrue(cursor.moveToFirst());
            assertEquals("alice", cursor.getString(0));
        } finally {
            cursor.close();
        }
    }

    @Test
    public void transactionRollback_shouldKeepOriginalData() {
        db.execSQL("DELETE FROM t_user");

        db.beginTransaction();
        try {
            db.execSQL("INSERT INTO t_user(id,name) VALUES(2,'bob')");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        db.beginTransaction();
        try {
            db.execSQL("UPDATE t_user SET name='charlie' WHERE id=2");
            // no setTransactionSuccessful -> rollback expected
        } finally {
            db.endTransaction();
        }

        Cursor cursor = db.query("SELECT name FROM t_user WHERE id = 2");
        try {
            assertTrue(cursor.moveToFirst());
            assertEquals("bob", cursor.getString(0));
        } finally {
            cursor.close();
        }
    }

    @Test
    public void insertConflictIgnore_shouldNotReplaceExistingRow() {
        db.execSQL("DELETE FROM t_user");

        ContentValues first = new ContentValues();
        first.put("id", 10);
        first.put("name", "origin");
        db.insert("t_user", 0, first);

        ContentValues conflict = new ContentValues();
        conflict.put("id", 10);
        conflict.put("name", "new_name");
        db.insert("t_user", 4, conflict); // CONFLICT_IGNORE

        Cursor cursor = db.query("SELECT name FROM t_user WHERE id = 10");
        try {
            assertTrue(cursor.moveToFirst());
            assertEquals("origin", cursor.getString(0));
        } finally {
            cursor.close();
        }
    }
}
