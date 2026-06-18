package com.example.schedulemanager.ui.week

import android.view.View
import com.example.schedulemanager.data.ScheduleEntity
import com.example.schedulemanager.databinding.ActivityMainBinding
import com.example.schedulemanager.external.Holiday
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

class WeeklyTimetableController(
    private val binding: ActivityMainBinding,
    private val titleFormatter: DateTimeFormatter,
    private val onScheduleClick: (ScheduleEntity) -> Unit,
    private val onScheduleDrop: (Long, LocalDate, Int, Int) -> Unit,
    private val onPinch: (Float) -> Unit,
    private val onFocusChanged: (LocalDate) -> Unit
) {
    var schedules: List<ScheduleEntity> = emptyList()
        set(value) {
            field = value
            render()
        }

    var holidays: List<Holiday> = emptyList()
        set(value) {
            field = value
            render()
        }

    var selectedDate: LocalDate = LocalDate.now()
        private set

    var selectedWeekStart: LocalDate = weekStart(LocalDate.now())
        private set

    var focusedDay: Int = LocalDate.now().dayOfWeek.value
        private set

    init {
        binding.weekHeader.headerOnly = true
        binding.weekHeader.onDayFocus = { onFocusChanged(it) }
        binding.weekHeader.onPinch = { onPinch(it) }
        binding.weekHeader.onFocusPositionChanged = { syncFocusPosition(it) }
        binding.weekSchedule.onScheduleClick = { onScheduleClick(it) }
        binding.weekSchedule.onDayFocus = { onFocusChanged(it) }
        binding.weekSchedule.onPinch = { onPinch(it) }
        binding.weekSchedule.onFocusPositionChanged = { syncFocusPosition(it) }
        binding.weekSchedule.onScheduleDrop = { id, date, day, minutes ->
            onScheduleDrop(id, date, day, minutes)
        }
    }

    fun focusDate(date: LocalDate) {
        selectedDate = date
        selectedWeekStart = weekStart(date)
        focusedDay = date.dayOfWeek.value
        render()
    }

    fun focusToday() {
        focusDate(LocalDate.now())
    }

    fun dateForDay(day: Int): LocalDate = selectedWeekStart.plusDays((day - 1).toLong())

    fun render() {
        val weekEnd = selectedWeekStart.plusDays(6)
        binding.titleText.text = "${selectedWeekStart.format(titleFormatter)} - ${weekEnd.format(titleFormatter)}"

        binding.weekHeader.selectedWeekStart = selectedWeekStart
        binding.weekHeader.focusedDay = focusedDay
        binding.weekHeader.holidays = holidays

        binding.weekSchedule.schedules = schedules
        binding.weekSchedule.selectedWeekStart = selectedWeekStart
        binding.weekSchedule.focusedDay = focusedDay
        binding.weekSchedule.holidays = holidays
    }

    fun isWeekVisible(): Boolean {
        return binding.weekScroll.visibility == View.VISIBLE &&
            binding.monthFragmentContainer.visibility != View.VISIBLE
    }

    private fun syncFocusPosition(position: Float?) {
        if (position != null) {
            binding.weekHeader.prepareFocusSettle(position)
            binding.weekSchedule.prepareFocusSettle(position)
        }
        binding.weekHeader.interactiveFocusPosition = position
        binding.weekSchedule.interactiveFocusPosition = position
    }

    companion object {
        fun weekStart(date: LocalDate): LocalDate {
            return date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        }
    }
}
