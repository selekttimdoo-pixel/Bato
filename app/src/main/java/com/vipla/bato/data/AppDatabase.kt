package com.vipla.bato.data

import android.content.Context
import androidx.room.*

@Database(entities = [StenoEvent::class, CockpitEvent::class, ControlState::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stenoDao(): StenoDao
    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "vipla_bato_cockpit.db")
                .fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}
