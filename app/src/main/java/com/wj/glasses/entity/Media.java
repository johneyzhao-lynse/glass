package com.wj.glasses.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "media")
public class Media {
    @PrimaryKey(autoGenerate = true)
    public int id;
    @ColumnInfo(name = "name")
    public String name;
    @ColumnInfo(name = "path")
    public String path;
    @ColumnInfo(name = "mediaType")
    public int mediaType;
}
