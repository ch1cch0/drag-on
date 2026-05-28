package com.example.schedulemanager

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.schedulemanager.databinding.FragmentMonthCalendarBinding
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class MonthCalendarFragment : Fragment() {
    interface Callbacks {
        fun onMonthDateSelected(date: LocalDate)
        fun onMonthChanged(month: LocalDate)
        fun onMonthTodaySelected()
        fun onMonthTitleSelected(month: LocalDate)
    }

    private var _binding: FragmentMonthCalendarBinding? = null
    private val binding get() = _binding!!
    private val firstMonth = LocalDate.now().withDayOfMonth(1).minusYears(10)
    private val monthCount = 10 * 12 * 2 + 1
    private var suppressPageCallback = false
    private var currentDisplayedMonth: LocalDate = LocalDate.now().withDayOfMonth(1)

    var displayedMonth: LocalDate
        get() = currentDisplayedMonth
        set(value) {
            val month = value.withDayOfMonth(1)
            if (month == currentDisplayedMonth) return
            currentDisplayedMonth = month
            if (_binding != null) render()
        }
    var selectedDate: LocalDate = LocalDate.now()
        set(value) {
            field = value
            if (_binding != null) render()
        }
    var holidays: List<HolidayItem> = emptyList()
        set(value) {
            field = value
            if (_binding != null) render()
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMonthCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.monthPager.adapter = MonthPagerAdapter()
        binding.monthPager.offscreenPageLimit = 1
        binding.monthPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    if (suppressPageCallback) return
                    currentDisplayedMonth = monthForPosition(position)
                    updateMonthTitle()
                    callbacks()?.onMonthChanged(currentDisplayedMonth)
                }
            }
        )
        binding.previousMonthButton.setOnClickListener {
            binding.monthPager.setCurrentItem(positionForMonth(displayedMonth.minusMonths(1)), true)
        }
        binding.nextMonthButton.setOnClickListener {
            binding.monthPager.setCurrentItem(positionForMonth(displayedMonth.plusMonths(1)), true)
        }
        binding.todayButton.setOnClickListener {
            callbacks()?.onMonthTodaySelected()
        }
        binding.monthTitleText.setOnClickListener {
            callbacks()?.onMonthTitleSelected(displayedMonth)
        }
        render()
    }

    override fun onDestroyView() {
        binding.monthPager.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private fun render() {
        updateMonthTitle()
        val position = positionForMonth(displayedMonth)
        if (binding.monthPager.currentItem != position) {
            suppressPageCallback = true
            binding.monthPager.setCurrentItem(position, false)
            binding.monthPager.post { suppressPageCallback = false }
        }
        binding.monthPager.adapter?.notifyDataSetChanged()
    }

    private fun monthForPosition(position: Int): LocalDate {
        return firstMonth.plusMonths(position.coerceIn(0, monthCount - 1).toLong())
    }

    private fun positionForMonth(month: LocalDate): Int {
        return ChronoUnit.MONTHS.between(firstMonth, month.withDayOfMonth(1))
            .toInt()
            .coerceIn(0, monthCount - 1)
    }

    private fun callbacks(): Callbacks? = activity as? Callbacks

    private fun updateMonthTitle() {
        binding.monthTitleText.text = "${displayedMonth.year}년 ${displayedMonth.monthValue}월"
    }

    private inner class MonthPagerAdapter : RecyclerView.Adapter<MonthViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MonthViewHolder {
            val calendarView = MonthCalendarView(parent.context).apply {
                showNavigation = false
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
                setPadding(dp(6), dp(6), dp(6), dp(6))
            }
            val container = FrameLayout(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                addView(calendarView)
            }
            return MonthViewHolder(container, calendarView)
        }

        override fun onBindViewHolder(holder: MonthViewHolder, position: Int) {
            holder.bind(monthForPosition(position))
        }

        override fun getItemCount(): Int = monthCount
    }

    private inner class MonthViewHolder(
        itemView: View,
        private val calendarView: MonthCalendarView
    ) : RecyclerView.ViewHolder(itemView) {
        fun bind(month: LocalDate) {
            calendarView.displayedMonth = month
            calendarView.selectedDate = selectedDate
            calendarView.holidays = holidays
            calendarView.onDateSelected = { callbacks()?.onMonthDateSelected(it) }
            calendarView.onMonthChanged = null
            calendarView.onTodaySelected = null
            calendarView.onMonthTitleSelected = null
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
