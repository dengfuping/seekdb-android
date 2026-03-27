package com.oceanbase.seekdb.android.room;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "room_user")
public class UserEntity {
    @PrimaryKey
    public long id;

    public String name;
}
