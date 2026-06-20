package com.example.schedulemanager.ui.schedule

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.AdapterView
import android.widget.DatePicker
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.Spinner
import android.widget.TextView
import com.example.schedulemanager.R
import com.example.schedulemanager.data.CategoryEntity
import com.example.schedulemanager.data.RepeatType
import com.example.schedulemanager.data.ScheduleEntity
import com.example.schedulemanager.data.ScheduleStatus
import com.example.schedulemanager.external.KakaoPlace
import com.example.schedulemanager.ui.common.DialogFormViews
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
    private val formViews = DialogFormViews(context)

    fun show() {
        // 💡 1. 순수 Dialog 객체를 생성하여 기본 프레임 완전 제거
        val dialog = Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
        }

        // 전체 마진용 외곽 패딩 컨테이너
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val margin = formViews.dp(24)
            setPadding(margin, margin, margin, margin)
        }

        // 💡 2. 실제 흰색 라운드 배경을 갖는 일체형 메인 카드뷰 레이아웃
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(formViews.dp(24), formViews.dp(24), formViews.dp(24), formViews.dp(24))
            background = android.graphics.drawable.GradientDrawable().apply {
                color = android.content.res.ColorStateList.valueOf(Color.WHITE)
                cornerRadius = formViews.dp(16).toFloat()
            }
        }

        // 💡 3. 타이틀 텍스트를 카드 내부 최상단 자식으로 배치
        val dialogTitleText = TextView(context).apply {
            text = if (schedule == null) "Add schedule" else "Edit schedule"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(24, 32, 42))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = formViews.dp(16)
            }
        }
        card.addView(dialogTitleText)

        // 기존 폼 입력 내용들 생성
        val form = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

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
            compoundDrawablePadding = formViews.dp(10)
            setPadding(formViews.dp(12), 0, formViews.dp(12), 0)
            minHeight = formViews.dp(48)
        }
        deadlineInput.setOnClickListener {
            showDeadlinePicker(selectedDeadline) { date ->
                selectedDeadline = date
                deadlineInput.setText(date?.format(deadlineFormatter).orEmpty())
            }
        }

        categorySpinner.adapter = formViews.simpleAdapter(listOf("Unset") + categories.map { it.name })
        categorySpinner.setSelection(categories.indexOfFirst { it.id == schedule?.categoryId }.takeIf { it >= 0 }?.plus(1) ?: 0)
        colorSpinner.adapter = formViews.simpleAdapter(listOf("Unset") + colorOptions.map { it.first })
        colorSpinner.setSelection(colorOptions.indexOfFirst { it.second == schedule?.color }.takeIf { it >= 0 }?.plus(1) ?: 0)
        repeatModeSpinner.adapter = formViews.simpleAdapter(listOf("Unset", "One-time", "Weekly", "Daily"))
        repeatModeSpinner.setSelection(
            when (schedule?.repeatType) {
                null -> 0
                RepeatType.NONE -> 1
                RepeatType.WEEKLY -> 2
                RepeatType.DAILY -> 3
            }
        )

        form.addView(formViews.label("Title"))
        form.addView(titleInput)
        form.addView(formViews.label("Location"))
        form.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                addView(locationInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(
                    MaterialButton(context).apply {
                        text = "Search"
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, formViews.dp(48)).apply {
                            marginStart = formViews.dp(8)
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
        form.addView(formViews.label("Category"))
        form.addView(categorySpinner)
        form.addView(formViews.label("Color"))
        form.addView(colorSpinner)
        form.addView(formViews.label("Repeat / One-time"))
        form.addView(repeatModeSpinner)
        form.addView(formViews.label("Duration"))
        form.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(hourPicker, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(minutePicker, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
        )
        form.addView(formViews.label("Deadline"))
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

        // 입력 폼 부분을 스크롤Wrap 처리하여 본문에 탑재
        val scrollBody = formViews.scrollWrap(form).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        card.addView(scrollBody)

        // 💡 4. 하단 버튼 영역 커스텀 생성 (카드 내부 최하단 자식으로 밀어 넣기)
        val bottomActions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = formViews.dp(24)
            }
        }

        val cancelButton = MaterialButton(context, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = "Cancel"
            setTextColor(Color.rgb(102, 112, 133))
            setOnClickListener { dialog.dismiss() }
        }

        val saveButton = MaterialButton(context).apply {
            text = "Save"
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(103, 58, 183)) // 퍼플 색상 반영
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = formViews.dp(8)
            }
        }

        bottomActions.addView(cancelButton)
        bottomActions.addView(saveButton)
        card.addView(bottomActions)

        container.addView(card)
        dialog.setContentView(container)

        // 저장 세팅 리스너 연동
        saveButton.setOnClickListener {
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

        // 투명 뒷배경 및 가로 MATCH_PARENT 강제 가득 채우기 설정
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
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
            durationMinutes = ((hourPicker.value * 60) + formViews.minuteIndexToMinutes(minutePicker.value)).takeIf { it > 0 },
            deadline = deadline?.toEpochDay(),
            locationName = location.name,
            locationAddress = if (locationChanged) null else location.address,
            locationLatitude = if (locationChanged) null else location.latitude,
            locationLongitude = if (locationChanged) null else location.longitude,
            googleCalendarId = schedule?.googleCalendarId,
            googleEventId = schedule?.googleEventId,
            googleSyncedAt = schedule?.googleSyncedAt,
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
        android.app.AlertDialog.Builder(context)
            .setTitle("Deadline")
            .setView(picker)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Clear") { _, _ -> onSelected(null) }
            .setPositiveButton("Set") { _, _ ->
                onSelected(LocalDate.of(picker.year, picker.month + 1, picker.dayOfMonth))
            }
            .show()
    }

    private data class LocationSelection(
        val name: String?,
        val selectedName: String?,
        val address: String?,
        val latitude: Double?,
        val longitude: Double?
    )
}