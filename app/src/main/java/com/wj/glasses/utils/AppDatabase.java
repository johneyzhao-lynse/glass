package com.wj.glasses.utils;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.wj.glasses.entity.Media;

@Database(entities = {Media.class},version = 1,exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract MediaDao getMediaDao();
}
