package com.air.quality.meter.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.air.quality.meter.data.model.AQIRecord

/**
 * Room database for the Air Quality app.
 *
 * Current entities:
 *  - AQIRecord  → local cache of fetched & manual AQI readings
 *
 * Future entities can be added here with a version bump + migration.
 */
@Database(
    entities = [AQIRecord::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun aqiRecordDao(): AQIRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "air_quality_db"
                )
                    .fallbackToDestructiveMigration()   // safe during development; replace with migrations in production
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
