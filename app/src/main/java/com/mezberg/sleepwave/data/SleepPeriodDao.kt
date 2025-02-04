package com.mezberg.sleepwave.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.*

@Dao
interface SleepPeriodDao {
    @Query("SELECT * FROM sleep_periods ORDER BY start DESC")
    fun getAllSleepPeriods(): Flow<List<SleepPeriodEntity>>

    @Query("SELECT * FROM sleep_periods WHERE id = :id")
    suspend fun getSleepPeriodById(id: Long): SleepPeriodEntity?

    @Query("SELECT * FROM sleep_periods ORDER BY end DESC LIMIT 1")
    suspend fun getLatestSleepPeriod(): SleepPeriodEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM sleep_periods WHERE start = :start AND end = :end)")
    suspend fun doesSleepPeriodExist(start: Date, end: Date): Boolean

    @Query("SELECT * FROM sleep_periods WHERE start BETWEEN :startDate AND :endDate ORDER BY start DESC")
    suspend fun getSleepPeriodsBetweenDates(startDate: Date, endDate: Date): List<SleepPeriodEntity>

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM sleep_periods 
            WHERE (start BETWEEN :newStart AND :newEnd) 
            OR (end BETWEEN :newStart AND :newEnd)
            OR (:newStart BETWEEN start AND end)
            OR (:newEnd BETWEEN start AND end)
        )
    """)
    suspend fun hasOverlappingPeriods(newStart: Date, newEnd: Date): Boolean

    @Insert
    suspend fun insert(sleepPeriod: SleepPeriodEntity): Long

    @Insert
    suspend fun insertAll(sleepPeriods: List<SleepPeriodEntity>)

    @Update
    suspend fun update(sleepPeriod: SleepPeriodEntity)

    @Delete
    suspend fun delete(sleepPeriod: SleepPeriodEntity)

    @Query("DELETE FROM sleep_periods")
    suspend fun deleteAll()
} 