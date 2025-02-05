package com.mezberg.sleepwave.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SleepPeriodEntity::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class SleepDatabase : RoomDatabase() {
    abstract fun sleepPeriodDao(): SleepPeriodDao

    companion object {
        const val NIGHT_START_HOUR = 19 // 7 PM
        const val NIGHT_END_HOUR = 6 // 6 AM

        @Volatile
        private var INSTANCE: SleepDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add the sleepDate column with a default value of the end date
                database.execSQL(
                    "ALTER TABLE sleep_periods ADD COLUMN sleepDate INTEGER NOT NULL DEFAULT 0"
                )
                
                // Update sleepDate based on the specified logic:
                // If both start and end hours are between NIGHT_START_HOUR and midnight, 
                // then sleepDate should be end date + 1 day (date only), otherwise end date (date only)
                database.execSQL("""
                    UPDATE sleep_periods 
                    SET sleepDate = CASE 
                        WHEN strftime('%H', datetime(start/1000, 'unixepoch')) >= '$NIGHT_START_HOUR'
                        AND strftime('%H', datetime(start/1000, 'unixepoch')) <= '23'
                        AND strftime('%H', datetime(end/1000, 'unixepoch')) >= '$NIGHT_START_HOUR'
                        AND strftime('%H', datetime(end/1000, 'unixepoch')) <= '23'
                        THEN (
                            strftime('%s', date(datetime(end/1000, 'unixepoch', '+1 day'))) * 1000
                        )
                        ELSE (
                            strftime('%s', date(datetime(end/1000, 'unixepoch'))) * 1000
                        )
                    END
                """)
            }
        }

        fun getDatabase(context: Context): SleepDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SleepDatabase::class.java,
                    "sleep_database"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
} 