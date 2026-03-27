package com.oceanbase.seekdb.android.room;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface UserDao {
    @Insert
    void insert(UserEntity user);

    @Query("SELECT * FROM room_user WHERE id = :id")
    UserEntity findById(long id);

    @Query("SELECT * FROM room_user ORDER BY id")
    List<UserEntity> findAll();
}
