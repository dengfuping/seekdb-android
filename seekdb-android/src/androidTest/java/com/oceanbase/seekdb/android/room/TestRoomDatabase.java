package com.oceanbase.seekdb.android.room;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {UserEntity.class}, version = 1, exportSchema = false)
public abstract class TestRoomDatabase extends RoomDatabase {
    public abstract UserDao userDao();
}
