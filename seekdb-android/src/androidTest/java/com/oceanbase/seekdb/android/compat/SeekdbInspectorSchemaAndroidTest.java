package com.oceanbase.seekdb.android.compat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import androidx.room.Room;
import androidx.sqlite.db.SimpleSQLiteQuery;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.oceanbase.seekdb.android.nativeapi.SeekdbClient;
import com.oceanbase.seekdb.android.room.TestRoomDatabase;
import com.oceanbase.seekdb.android.room.UserEntity;
import java.util.HashSet;
import java.util.Set;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Verifies the same schema SQL used by App Inspection ({@link SeekdbInspectorSchemaQuery}) runs on
 * an embedded SeekDB-backed Room database and returns {@code room_user} columns (and related
 * metadata columns expected by {@code SqliteInspector#querySchema}).
 */
@RunWith(AndroidJUnit4.class)
public class SeekdbInspectorSchemaAndroidTest {
    private TestRoomDatabase db;

    @Before
    public void setUp() {
        Assume.assumeTrue("Skip when libseekdb.so is unavailable", SeekdbClient.isNativeAvailable());
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.databaseBuilder(context, TestRoomDatabase.class, "inspector_schema_verify.db")
                .allowMainThreadQueries()
                .openHelperFactory(SeekdbCompat.factory())
                .build();
        UserEntity user = new UserEntity();
        user.id = 42;
        user.name = "inspector";
        db.userDao().insert(user);
    }

    @After
    public void tearDown() {
        if (db != null) {
            db.close();
        }
    }

    @Test
    public void appInspectionSchemaQuery_returnsRoomUserColumns() {
        try (Cursor c =
                db.getOpenHelper()
                        .getWritableDatabase()
                        .query(
                                new SimpleSQLiteQuery(
                                        SeekdbInspectorSchemaQuery.FOR_APP_INSPECTION_GET_SCHEMA))) {
            assertTrue(c.moveToFirst());
            int typeIx = c.getColumnIndex("type");
            int tableIx = c.getColumnIndex("tableName");
            int colIx = c.getColumnIndex("columnName");
            int typeColIx = c.getColumnIndex("columnType");
            int nnIx = c.getColumnIndex("notnull");
            int pkIx = c.getColumnIndex("pk");
            int uqIx = c.getColumnIndex("unique");
            assertTrue("type", typeIx >= 0);
            assertTrue("tableName", tableIx >= 0);
            assertTrue("columnName", colIx >= 0);
            assertTrue("columnType", typeColIx >= 0);
            assertTrue("notnull", nnIx >= 0);
            assertTrue("pk", pkIx >= 0);
            assertTrue("unique", uqIx >= 0);

            Set<String> columnsForRoomUser = new HashSet<>();
            do {
                if ("room_user".equalsIgnoreCase(c.getString(tableIx))) {
                    columnsForRoomUser.add(c.getString(colIx));
                    assertEquals("table", c.getString(typeIx).toLowerCase(java.util.Locale.ROOT));
                    assertNotNull(c.getString(typeColIx));
                }
            } while (c.moveToNext());

            assertTrue(columnsForRoomUser.contains("id"));
            assertTrue(columnsForRoomUser.contains("name"));
        }
    }
}
