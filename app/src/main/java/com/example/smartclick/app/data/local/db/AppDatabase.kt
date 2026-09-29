package com.example.smartclick.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TvDeviceEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tvDeviceDao(): TvDeviceDao
}
