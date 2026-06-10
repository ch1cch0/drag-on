package com.example.schedulemanager

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.DatePicker
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import com.example.schedulemanager.data.CategoryEntity
import com.example.schedulemanager.data.RepeatType
import com.example.schedulemanager.data.ScheduleEntity
import com.example.schedulemanager.data.ScheduleStatus
import com.google.android.material.button.MaterialButton
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ScheduleEditorDialog(
    private val context: Context,
    private val categories: List<CategoryEntity>,
    private val colorOptions: List<Pair<String, Int>>,
    private val schedule: ScheduleEntity?,
    private val onLocationSearch: (String, (KakaoPlace) -> Unit) -> Unit,
    private val onSave: (ScheduleEntity) -> Unit
) {
    private val deadlineFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")

    fun show() {
        val form = cardForm()
        val titleInput = EditText(context).apply {
            hint = "Title"
            setText(schedule?.title.orEmpty())
            setSingleLine(true)
        }
        var selectedLocationName: String? = schedule?.locationName
        var selectedLocationAddress: String? = schedule?.locationAddress
        var selectedLocationLatitude: Double? = schedule?.locationLatitude
        var selectedLocationLongitude: Double? = schedule?.locationLongitude
        val locationInput = EditText(context).apply {
            hint = "Location"
            setText(schedule?.locationName.orEmpty())
            setSingleLine(true)
        }
        val categorySpinner = Spinner(context)
        val colorSpinner = Spinner(context)
        val repeatModeSpinner = Spinner(context)
        val hourPicker = NumberPicker(context).apply {
            minValue = 0
            maxValue = 8
            wrapSelectorWheel = false
            value = (schedule?.durationMinutes ?: 0) / 60
        }
        val minuteValues = arrayOf("00", "15", "30", "45")
        val minutePicker = NumberPicker(context).apply {
            minValue = 0
            maxValue = minuteValues.lastIndex
            displayedValues = minuteValues
            wrapSelectorWheel = false
            value = when ((schedule?.durationMinutes ?: -1) % 60) {
                15 -> 1
                30 -> 2
                45 -> 3
                else -> 0
            }
        }

        var selectedDeadline: LocalDate? = schedule?.deadline?.let { LocalDate.ofEpochDay(it) }
        val deadlineInput = EditText(context).apply {
            hint = "YYYY.MM.DD"
            setText(selectedDeadline?.format(deadlineFormatter).orEmpty())
            setSingleLine(true)
            isFocusable = false
            isCursorVisible = false
            isClickable = true
            background = context.getDrawable(R.drawable.bg_date_input)
            setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_calendar_24, 0)
            compoundDrawablePadding = dp(10)
            setPadding(dp(12), 0, dp(12), 0)
            minHeight = dp(48)
        }
        deadlineInput.setOnClickListener {
            showDeadlinePicker(selectedDeadline) { date ->
                selectedDeadline = date
                deadlineInput.setText(date?.format(deadlineFormatter).orEmpty())
            }
        }

        categorySpinner.adapter = simpleAdapter(listOf("Unset") + categories.map { it.name })
        categorySpinner.setSelection(categories.indexOfFirst { it.id == schedule?.categoryId }.takeIf { it >= 0 }?.plus(1) ?: 0)
        colorSpinner.adapter = simpleAdapter(listOf("Unset") + colorOptions.map { it.first })
        colorSpinner.setSelection(colorOptions.indexOfFirst { it.second == schedule?.color }.takeIf { it >= 0 }?.plus(1) ?: 0)
        repeatModeSpinner.adapter = simpleAdapter(listOf("Unset", "One-time", "Weekly", "Daily"))
        repeatModeSpinner.setSelection(
            when (schedule?.repeatType) {
                null -> 0
                RepeatType.NONE -> 1
                RepeatType.WEEKLY -> 2
                RepeatType.DAILY -> 3
            }
        )

        form.addView(label("Title"))
        form.addView(titleInput)
        form.addView(label("Location"))
        form.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                addView(locationInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(
                    MaterialButton(context).apply {
                        text = "Search"
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)).apply {
                            marginStart = dp(8)
                        }
                        setOnClickListener {
                            onLocationSearch(locationInput.text.toString().trim()) { place ->
                                selectedLocationName = place.name
                                selectedLocationAddress = place.address
                                selectedLocationLatitude = place.latitude
                                selectedLocationLongitude = place.longitude
                                locationInput.setText(place.name)
                            }
                        }
                    }
                )
            }
        )
        form.addView(label("Category"))
        form.addView(categorySpinner)
        form.addView(label("Color"))
        form.addView(colorSpinner)
        form.addView(label("Repeat / One-time"))
        form.addView(repeatModeSpinner)
        form.addView(label("Duration"))
        form.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(hourPicker, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(minutePicker, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
        )
        form.addView(label("Deadline"))
        form.addView(deadlineInput)

        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position <= 0) return
                val category = categories[position - 1]
                if (schedule?.durationMinutes == null && hourPicker.value == 0 && minutePicker.value == 0) {
                    category.defaultDurationMinutes?.let {
                        hourPicker.value = it / 60
                        minutePicker.value = (it % 60) / 15
                    }
                }
                if (schedule?.repeatType == null && repeatModeSpinner.selectedItemPosition == 0) {
                    category.defaultRepeatType?.let { repeatType ->
                        repeatModeSpinner.setSelection(
                            when (repeatType) {
                                RepeatType.NONE -> 1
                                RepeatType.WEEKLY -> 2
                                RepeatType.DAILY -> 3
                            }
                        )
                    }
                }
                category.defaultColor?.let { defaultColor ->
                    val colorIndex = colorOptions.indexOfFirst { it.second == defaultColor }
                    if (schedule?.color == null && colorSpinner.selectedItemPosition == 0 && colorIndex >= 0) {
                        colorSpinner.setSelection(colorIndex + 1)
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle(if (schedule == null) "Add schedule" else "Edit schedule")
            .setView(scrollWrap(form))
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val entity = buildSchedule(
                    titleInput = titleInput,
                    categorySpinner = categorySpinner,
                    colorSpinner = colorSpinner,
                    repeatModeSpinner = repeatModeSpinner,
                    hourPicker = hourPicker,
                    minutePicker = minutePicker,
                    deadline = selectedDeadline,
                    location = LocationSelection(
                        name = locationInput.text.toString().trim().takeIf { it.isNotBlank() },
                        selectedName = selectedLocationName,
                        address = selectedLocationAddress,
                        latitude = selectedLocationLatitude,
                        longitude = selectedLocationLongitude
                    )
                ) ?: return@setOnClickListener
                onSave(entity)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun buildSchedule(
        titleInput: EditText,
        categorySpinner: Spinner,
        colorSpinner: Spinner,
        repeatModeSpinner: Spinner,
        hourPicker: NumberPicker,
        minutePicker: NumberPicker,
        deadline: LocalDate?,
        location: LocationSelection
    ): ScheduleEntity? {
        val title = titleInput.text.toString().trim()
        if (title.isBlank()) {
            titleInput.error = "Required"
            return null
        }
        val repeatType = when (repeatModeSpinner.selectedItemPosition) {
            1 -> RepeatType.NONE
            2 -> RepeatType.WEEKLY
            3 -> RepeatType.DAILY
            else -> null
        }
        val selectedCategory = categorySpinner.selectedItemPosition.takeIf { it > 0 }?.let { categories[it - 1] }
        val locationChanged = location.name != location.selectedName
        return ScheduleEntity(
            id = schedule?.id ?: 0,
            title = title,
            categoryId = selectedCategory?.id,
            color = colorSpinner.selectedItemPosition.takeIf { it > 0 }?.let { colorOptions[it - 1].second },
            isRepeat = repeatType?.let { it != RepeatType.NONE },
            repeatType = repeatType,
            durationMinutes = ((hourPicker.value * 60) + minuteIndexToMinutes(minutePicker.value)).takeIf { it > 0 },
            deadline = deadline?.toEpochDay(),
            locationName = location.name,
            locationAddress = if (locationChanged) null else location.address,
            locationLatitude = if (locationChanged) null else location.latitude,
            locationLongitude = if (locationChanged) null else location.longitude,
            scheduledDate = schedule?.scheduledDate,
            dayOfWeek = schedule?.dayOfWeek,
            startTimeMinutes = schedule?.startTimeMinutes,
            status = schedule?.status ?: ScheduleStatus.INBOX
        )
    }

    private fun showDeadlinePicker(current: LocalDate?, onSelected: (LocalDate?) -> Unit) {
        val initial = current ?: LocalDate.now()
        val picker = DatePicker(context).apply {
            init(initial.year, initial.monthValue - 1, initial.dayOfMonth, null)
        }
        AlertDialog.Builder(context)
            .setTitle("Deadline")
            .setView(picker)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Clear") { _, _ -> onSelected(null) }
            .setPositiveButton("Set") { _, _ ->
                onSelected(LocalDate.of(picker.year, picker.month + 1, picker.dayOfMonth))
            }
            .show()
    }

    private fun simpleAdapter(values: List<String>): ArrayAdapter<String> {
        return ArrayAdapter(context, android.R.layout.simple_spinner_item, values).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun cardForm(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(12), dp(20), dp(12))
        background = cellBackground(Color.WHITE, Color.TRANSPARENT, 14)
    }

    private fun label(text: String): TextView = TextView(context).apply {
        this.text = text
        setPadding(0, dp(12), 0, dp(2))
        setTextColor(Color.rgb(102, 112, 133))
        textSize = 12f
    }

    private fun scrollWrap(content: View): ScrollView = ScrollView(context).apply {
        addView(content)
    }

    private fun cellBackground(fill: Int, stroke: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            color = android.content.res.ColorStateList.valueOf(fill)
            cornerRadius = dp(radiusDp).toFloat()
            if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
        }
    }

    private fun minuteIndexToMinutes(index: Int): Int {
        return when (index) {
            1 -> 15
            2 -> 30
            3 -> 45
            else -> 0
        }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private data class LocationSelection(
        val name: String?,
        val selectedName: String?,
        val address: String?,
        val latitude: Double?,
        val longitude: Double?
    )
}
