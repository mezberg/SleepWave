package com.mezberg.sleepwave.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.*

@Dao
interface SleepPeriodDao {
    @Query("SELECT * FROM sleep_periods WHERE isDeleted = 0 ORDER BY start DESC")
    fun getAllSleepPeriods(): Flow<List<SleepPeriodEntity>>

    @Query("SELECT * FROM sleep_periods WHERE id = :id AND isDeleted = 0")
    suspend fun getSleepPeriodById(id: Long): SleepPeriodEntity?

    @Query("SELECT * FROM sleep_periods ORDER BY end DESC LIMIT 1")
    suspend fun getLatestSleepPeriodIncludingDeleted(): SleepPeriodEntity?

    @Query("SELECT * FROM sleep_periods WHERE isDeleted = 0 ORDER BY end DESC LIMIT 1")
    suspend fun getLatestSleepPeriod(): SleepPeriodEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM sleep_periods WHERE start = :start AND end = :end AND isDeleted = 0)")
    suspend fun doesSleepPeriodExist(start: Date, end: Date): Boolean

    @Query("SELECT * FROM sleep_periods WHERE start BETWEEN :startDate AND :endDate AND isDeleted = 0 ORDER BY start DESC")
    suspend fun getSleepPeriodsBetweenDates(startDate: Date, endDate: Date): List<SleepPeriodEntity>

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM sleep_periods 
            WHERE isDeleted = 0
            AND (
                (start BETWEEN :newStart AND :newEnd) 
                OR (end BETWEEN :newStart AND :newEnd)
                OR (:newStart BETWEEN start AND end)
                OR (:newEnd BETWEEN start AND end)
            )
        )
    """)
    suspend fun hasOverlappingPeriods(newStart: Date, newEnd: Date): Boolean

    @Insert
    suspend fun insert(sleepPeriod: SleepPeriodEntity): Long

    @Insert
    suspend fun insertAll(sleepPeriods: List<SleepPeriodEntity>)

    @Update
    suspend fun update(sleepPeriod: SleepPeriodEntity)

    @Query("UPDATE sleep_periods SET isDeleted = 1 WHERE id = :id")
    suspend fun markAsDeleted(id: Long)

    @Delete
    suspend fun delete(sleepPeriod: SleepPeriodEntity)

    @Query("DELETE FROM sleep_periods")
    suspend fun deleteAll()

    @Query("""
        SELECT sleepDate as date, SUM(duration) / 60.0 as totalSleepHours
        FROM sleep_periods
        WHERE isDeleted = 0
        AND sleepDate >= :startDate
        AND sleepDate <= :endDate
        GROUP BY sleepDate
        ORDER BY sleepDate ASC
    """)
    fun getWeeklySleepData(startDate: Date, endDate: Date): Flow<List<WeeklySleepData>>

    @Query("SELECT * FROM sleep_periods WHERE sleepDate = :date AND isDeleted = 0 ORDER BY start DESC")
    suspend fun getSleepPeriodsByDate(date: Date): List<SleepPeriodEntity>
}

data class WeeklySleepData(
    val date: Date,
    val totalSleepHours: Float
) 