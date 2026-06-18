package com.example.schedulemanager

import com.example.schedulemanager.data.RepeatType
import com.example.schedulemanager.data.ScheduleEntity
import com.example.schedulemanager.data.ScheduleStatus
import com.example.schedulemanager.external.google.GoogleCalendarMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class GoogleCalendarMapperTest {
    private val zoneId: ZoneId = ZoneId.of("Asia/Seoul")

    @Test
    fun oneTimeScheduleHasNoRecurrenceRule() {
        val event = GoogleCalendarMapper.toEventJson(schedule(repeatType = RepeatType.NONE), zoneId)

        assertFalse(event.has("recurrence"))
    }

    @Test
    fun dailyScheduleUsesDailyRRule() {
        val event = GoogleCalendarMapper.toEventJson(schedule(repeatType = RepeatType.DAILY), zoneId)

        assertEquals("RRULE:FREQ=DAILY", event.getJSONArray("recurrence").getString(0))
    }

    @Test
    fun weeklyScheduleUsesWeeklyRRule() {
        val event = GoogleCalendarMapper.toEventJson(schedule(repeatType = RepeatType.WEEKLY), zoneId)

        assertEquals("RRULE:FREQ=WEEKLY", event.getJSONArray("recurrence").getString(0))
    }

    @Test
    fun scheduleTimeAndLocationAreMapped() {
        val event = GoogleCalendarMapper.toEventJson(schedule(repeatType = RepeatType.NONE), zoneId)

        assertEquals("Final exam", event.getString("summary"))
        assertEquals("Room 101", event.getString("location"))
        assertEquals("2026-06-16T09:15+09:00", event.getJSONObject("start").getString("dateTime"))
        assertEquals("2026-06-16T11:00+09:00", event.getJSONObject("end").getString("dateTime"))
        assertEquals("Asia/Seoul", event.getJSONObject("start").getString("timeZone"))
    }

    private fun schedule(repeatType: RepeatType?): ScheduleEntity {
        val date = LocalDate.of(2026, 6, 16)
        return ScheduleEntity(
            id = 42,
            title = "Final exam",
            categoryId = null,
            color = null,
            isRepeat = repeatType?.let { it != RepeatType.NONE },
            repeatType = repeatType,
            durationMinutes = 105,
            deadline = null,
            locationName = "Room 101",
            locationAddress = null,
            locationLatitude = null,
            locationLongitude = null,
            googleCalendarId = null,
            googleEventId = null,
            googleSyncedAt = null,
            scheduledDate = date.toEpochDay(),
            dayOfWeek = date.dayOfWeek.value,
            startTimeMinutes = 9 * 60 + 15,
            status = ScheduleStatus.SCHEDULED
        )
    }
}
