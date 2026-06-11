package com.example.schedulemanager

import android.view.View
import com.example.schedulemanager.databinding.ActivityMainBinding

class MainSurfaceController(
    private val binding: ActivityMainBinding,
    private val inboxController: InboxBottomSheetController,
    private val weeklyTimetable: WeeklyTimetableController,
    private val onRenderMonthlyCalendar: () -> Unit
) {
    var calendarMode: Boolean = false
        private set

    fun handlePinchScale(scaleFactor: Float) {
        if (scaleFactor < 0.92f && !calendarMode) {
            calendarMode = true
            render()
        } else if (scaleFactor > 1.08f && calendarMode) {
            calendarMode = false
            render()
        }
    }

    fun showWeek() {
        calendarMode = false
        if (isWeekVisible()) {
            weeklyTimetable.render()
        } else {
            render()
        }
    }

    fun render() {
        binding.scheduleSurface.animate().alpha(0.78f).setDuration(80).withEndAction {
            if (calendarMode) {
                binding.titleText.visibility = View.GONE
                binding.weekScroll.visibility = View.GONE
                binding.monthFragmentContainer.visibility = View.VISIBLE
                inboxController.setHidden(true)
                onRenderMonthlyCalendar()
            } else {
                binding.titleText.visibility = View.VISIBLE
                binding.monthFragmentContainer.visibility = View.GONE
                inboxController.setHidden(false)
                binding.weekScroll.visibility = View.VISIBLE
                weeklyTimetable.render()
            }
            binding.scheduleSurface.animate().alpha(1f).setDuration(130).start()
        }.start()
    }

    fun isWeekVisible(): Boolean {
        return weeklyTimetable.isWeekVisible()
    }
}
