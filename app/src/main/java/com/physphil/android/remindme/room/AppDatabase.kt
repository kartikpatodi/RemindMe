package com.physphil.android.remindme.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.physphil.android.remindme.DATABASE_VERSION
import com.physphil.android.remindme.room.entities.Reminder

/**
 * Copyright (c) 2017 Phil Shadlyn
 */
@Database(entities = arrayOf(Reminder::class), version = DATABASE_VERSION)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao
}