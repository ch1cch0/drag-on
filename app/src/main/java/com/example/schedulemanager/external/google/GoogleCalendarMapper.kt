package com.example.schedulemanager.external.google

import com.example.schedulemanager.data.RepeatType
import com.example.schedulemanager.data.ScheduleEntity
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object GoogleCalendarMapper {
    fun toEventJson(schedule: ScheduleEntity, zoneId: ZoneId = ZoneId.systemDefault()): JSONObject {
        val date = schedule.scheduledDate?.let { LocalDate.ofEpochDay(it) }
            ?: error("Schedule has no date.")
        val startMinutes = schedule.startTimeMinutes ?: error("Schedule has no start time.")
        val duration = schedule.durationMinutes?.takeIf { it > 0 } ?: DEFAULT_DURATION_MINUTES
        val start = date.atStartOfDay(zoneId).plusMinutes(startMinutes.toLong())
        val end = start.plusMinutes(duration.toLong())

        return JSONObject().apply {
            put("summary", schedule.title)
            schedule.locationName?.let { put("location", it) }
            put(
                "start",
                JSONObject()
                    .put("dateTime", start.toOffsetDateTime().format(GOOGLE_DATE_TIME_FORMATTER))
                    .put("timeZone", zoneId.id)
            )
            put(
                "end",
                JSONObject()
                    .put("dateTime", end.toOffsetDateTime().format(GOOGLE_DATE_TIME_FORMATTER))
                    .put("timeZone", zoneId.id)
            )
            recurrenceRule(schedule.repeatType)?.let {
                put("recurrence", JSONArray().put(it))
            }
            put(
                "extendedProperties",
                JSONObject().put(
                    "private",
                    JSONObject()
                        .put("scheduleManagerId", schedule.id.toString())
                        .put("scheduleManagerSyncedAt", Instant.now().toString())
                )
            )
        }
    }

    private fun recurrenceRule(repeatType: RepeatType?): String? {
        return when (repeatType) {
            RepeatType.DAILY -> "RRULE:FREQ=DAILY"
            RepeatType.WEEKLY -> "RRULE:FREQ=WEEKLY"
            else -> null
        }
    }

    private const val DEFAULT_DURATION_MINUTES = 60
    private val GOOGLE_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
}
