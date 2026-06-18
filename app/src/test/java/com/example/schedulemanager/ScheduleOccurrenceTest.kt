package com.example.schedulemanager

import com.example.schedulemanager.data.RepeatType
import com.example.schedulemanager.data.ScheduleEntity
import com.example.schedulemanager.data.ScheduleStatus
import com.example.schedulemanager.domain.ScheduleOccurrence
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScheduleOccurrenceTest {
    private val monday = LocalDate.of(2026, 6, 8)

    @Test
    fun oneTimeScheduleOccursOnlyOnScheduledDate() {
        val schedule = schedule(repeatType = RepeatType.NONE, scheduledDate = monday)

        assertTrue(ScheduleOccurrence.occursOn(schedule, monday))
        assertFalse(ScheduleOccurrence.occursOn(schedule, monday.plusDays(1)))
    }

    @Test
    fun weeklyScheduleOccursOnSameWeekdayAcrossWeeks() {
        val schedule = schedule(repeatType = RepeatType.WEEKLY, scheduledDate = monday)

        assertTrue(ScheduleOccurrence.occursOn(schedule, monday.plusWeeks(1)))
        assertFalse(ScheduleOccurrence.occursOn(schedule, monday.plusDays(1)))
    }

    @Test
    fun dailyScheduleOccursEveryDayAfterPlacement() {
        val schedule = schedule(repeatType = RepeatType.DAILY, scheduledDate = monday)

        assertTrue(ScheduleOccurrence.occursOn(schedule, monday))
        assertTrue(ScheduleOccurrence.occursOn(schedule, monday.plusDays(3)))
    }

    @Test
    fun unscheduledScheduleDoesNotOccur() {
        val schedule = schedule(repeatType = RepeatType.DAILY, scheduledDate = null)

        assertFalse(ScheduleOccurrence.occursOn(schedule, monday))
    }

    private fun schedule(repeatType: RepeatType?, scheduledDate: LocalDate?): ScheduleEntity {
        return ScheduleEntity(
            id = 1,
            title = "Test",
            categoryId = null,
            color = null,
            isRepeat = repeatType?.let { it != RepeatType.NONE },
            repeatType = repeatType,
            durationMinutes = 60,
            deadline = null,
            locationName = null,
            locationAddress = null,
            locationLatitude = null,
            locationLongitude = null,
            scheduledDate = scheduledDate?.toEpochDay(),
            dayOfWeek = scheduledDate?.dayOfWeek?.value,
            startTimeMinutes = 9 * 60,
            status = ScheduleStatus.SCHEDULED
        )
    }
}
