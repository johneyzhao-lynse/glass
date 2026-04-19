package com.wj.glasses.utils;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import com.wj.glasses.entity.Media;

import java.util.List;

@Dao
public interface MediaDao {
    @Query("SELECT * FROM media")
    List<Media> getAll();
    @Query("SELECT * FROM media WHERE mediaType=:mediaType")
    List<Media> getMedia(int mediaType);
    @Insert
    void insert(Media media);
    @Delete
    void deleteOnePhoto(Media media);
}
