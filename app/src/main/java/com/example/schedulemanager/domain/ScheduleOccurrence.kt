package com.example.schedulemanager.domain

import com.example.schedulemanager.data.RepeatType
import com.example.schedulemanager.data.ScheduleEntity
import java.time.LocalDate

object ScheduleOccurrence {
    fun occursOn(schedule: ScheduleEntity, date: LocalDate): Boolean {
        val anchor = schedule.scheduledDate?.let { LocalDate.ofEpochDay(it) } ?: return false
        return when (schedule.repeatType) {
            RepeatType.DAILY -> true
            RepeatType.WEEKLY -> date.dayOfWeek == anchor.dayOfWeek
            else -> date == anchor
        }
    }

    fun previewDays(schedule: ScheduleEntity?, selectedDay: Int): IntRange {
        return if (schedule?.repeatType == RepeatType.DAILY) 1..7 else selectedDay..selectedDay
    }

    fun minutesToText(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)
}
