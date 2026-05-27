package com.example.schedulemanager.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun toRepeatType(value: String?): RepeatType? = value?.let { RepeatType.valueOf(it) }

    @TypeConverter
    fun fromRepeatType(value: RepeatType?): String? = value?.name

    @TypeConverter
    fun toScheduleStatus(value: String): ScheduleStatus = ScheduleStatus.valueOf(value)

    @TypeConverter
    fun fromScheduleStatus(value: ScheduleStatus): String = value.name
}
