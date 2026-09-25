package com.tomjxyz.shipinfo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SessionEntity::class, SampleEntity::class, RollWindowEntity::class, PinEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessions(): SessionDao
    abstract fun samples(): SampleDao
    abstract fun rollWindows(): RollWindowDao
    abstract fun pins(): PinDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "shipinfo.db")
                    .build()
                    .also { instance = it }
            }
    }
}
