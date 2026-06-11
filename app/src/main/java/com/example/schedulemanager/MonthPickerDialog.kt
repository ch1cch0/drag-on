package com.example.schedulemanager

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.NumberPicker
import java.time.LocalDate

class MonthPickerDialog(
    private val context: Context,
    private val currentMonth: LocalDate,
    private val onMonthSelected: (LocalDate) -> Unit
) {
    private val density = context.resources.displayMetrics.density

    fun show() {
        val minYear = currentMonth.year - 50
        val maxYear = currentMonth.year + 50
        val yearPicker = NumberPicker(context).apply {
            minValue = minYear
            maxValue = maxYear
            displayedValues = (minYear..maxYear).map { "${it}년" }.toTypedArray()
            value = currentMonth.year
            wrapSelectorWheel = false
        }
        val monthPicker = NumberPicker(context).apply {
            minValue = 1
            maxValue = 12
            displayedValues = (1..12).map { "${it}월" }.toTypedArray()
            value = currentMonth.monthValue
            wrapSelectorWheel = true
        }
        val pickerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), 0)
            addView(yearPicker, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(monthPicker, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

        AlertDialog.Builder(context)
            .setView(cardForm().apply { addView(pickerRow) })
            .setNegativeButton("Cancel", null)
            .setPositiveButton("OK") { _, _ ->
                onMonthSelected(LocalDate.of(yearPicker.value, monthPicker.value, 1))
            }
            .show()
    }

    private fun cardForm(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(12), dp(20), dp(12))
        background = GradientDrawable().apply {
            color = android.content.res.ColorStateList.valueOf(Color.WHITE)
            cornerRadius = dp(14).toFloat()
        }
    }

    private fun dp(value: Int): Int = (value * density).toInt()
}
