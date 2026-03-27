package com.oceanbase.seekdb.android.room;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.oceanbase.seekdb.android.compat.SeekdbCompat;
import com.oceanbase.seekdb.android.nativeapi.SeekdbClient;
import java.util.List;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RoomSeekdbIntegrationTest {
    private TestRoomDatabase db;

    @Before
    public void setUp() {
        Assume.assumeTrue("Skip when libseekdb.so is unavailable", SeekdbClient.isNativeAvailable());
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.databaseBuilder(context, TestRoomDatabase.class, "room_seekdb_integration.db")
                .allowMainThreadQueries()
                .openHelperFactory(SeekdbCompat.factory())
                .build();
    }

    @After
    public void tearDown() {
        if (db != null) {
            db.close();
        }
    }

    @Test
    public void roomInsertAndQuery_shouldWork() {
        UserEntity user = new UserEntity();
        user.id = 101;
        user.name = "neo";
        db.userDao().insert(user);

        UserEntity loaded = db.userDao().findById(101);
        assertNotNull(loaded);
        assertEquals("neo", loaded.name);
    }

    @Test
    public void roomTransaction_shouldCommit() {
        db.runInTransaction(() -> {
            UserEntity u1 = new UserEntity();
            u1.id = 1;
            u1.name = "a";
            db.userDao().insert(u1);

            UserEntity u2 = new UserEntity();
            u2.id = 2;
            u2.name = "b";
            db.userDao().insert(u2);
        });

        List<UserEntity> rows = db.userDao().findAll();
        assertTrue(rows.size() >= 2);
    }

    @Test
    public void roomTransaction_shouldRollbackOnFailure() {
        try {
            db.runInTransaction(() -> {
                UserEntity u1 = new UserEntity();
                u1.id = 201;
                u1.name = "rollback_a";
                db.userDao().insert(u1);

                UserEntity u2 = new UserEntity();
                u2.id = 202;
                u2.name = "rollback_b";
                db.userDao().insert(u2);

                throw new IllegalStateException("force rollback");
            });
            fail("Expected transaction failure");
        } catch (IllegalStateException expected) {
            // expected
        }

        List<UserEntity> rows = db.userDao().findAll();
        for (UserEntity row : rows) {
            assertTrue(row.id != 201 && row.id != 202);
        }
    }

    @Test
    public void roomInsertNullAndNumeric_shouldWork() {
        UserEntity user = new UserEntity();
        user.id = 301;
        user.name = null;
        db.userDao().insert(user);

        UserEntity loaded = db.userDao().findById(301);
        assertNotNull(loaded);
        assertEquals(301, loaded.id);
        assertEquals(null, loaded.name);
    }
}
