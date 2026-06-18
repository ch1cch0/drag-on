package com.example.schedulemanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val categoryId: Long?,
    val color: Int?,
    val isRepeat: Boolean?,
    val repeatType: RepeatType?,
    val durationMinutes: Int?,
    val deadline: Long?,
    val locationName: String?,
    val locationAddress: String?,
    val locationLatitude: Double?,
    val locationLongitude: Double?,
    val scheduledDate: Long?,
    val dayOfWeek: Int?,
    val startTimeMinutes: Int?,
    val status: ScheduleStatus
)
