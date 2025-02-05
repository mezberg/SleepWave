package com.mezberg.sleepwave.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "sleep_periods")
data class SleepPeriodEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val start: Date,
    val end: Date,
    val duration: Long,
    val isPotentialSleep: Boolean,
    val sleepDate: Date // The date to which this sleep period belongs
) 