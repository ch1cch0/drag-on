package com.example.schedulemanager

import android.app.AlertDialog
import android.content.ClipData
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.schedulemanager.data.AppDatabase
import com.example.schedulemanager.data.CategoryEntity
import com.example.schedulemanager.data.RepeatType
import com.example.schedulemanager.data.ScheduleEntity
import com.example.schedulemanager.data.ScheduleRepository
import com.example.schedulemanager.data.ScheduleStatus
import com.example.schedulemanager.databinding.ActivityMainBinding
import com.example.schedulemanager.databinding.ItemScheduleBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: ScheduleRepository
    private lateinit var inboxAdapter: ScheduleAdapter
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>
    private lateinit var gestureDetector: GestureDetectorCompat
    private lateinit var scaleDetector: android.view.ScaleGestureDetector

    private var schedules: List<ScheduleEntity> = emptyList()
    private var categories: List<CategoryEntity> = emptyList()
    private var selectedDate: LocalDate = LocalDate.now()
    private var selectedWeekStart: LocalDate = weekStart(LocalDate.now())
    private var focusedDay = LocalDate.now().dayOfWeek.value
    private var displayedMonth: LocalDate = LocalDate.now().withDayOfMonth(1)
    private var calendarMode = false

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val deadlineFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
    private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
    private val shortDateFormatter = DateTimeFormatter.ofPattern("M/d")
    private val colorOptions = listOf(
        "Blue" to Color.rgb(34, 108, 224),
        "Green" to Color.rgb(46, 160, 67),
        "Orange" to Color.rgb(230, 126, 34),
        "Red" to Color.rgb(214, 48, 49),
        "Purple" to Color.rgb(123, 97, 255)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val database = AppDatabase.getInstance(this)
        repository = ScheduleRepository(database.scheduleDao(), database.categoryDao())

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bottomSheetBehavior = BottomSheetBehavior.from(binding.inboxSheet)
        inboxAdapter = ScheduleAdapter(
            onClick = { showScheduleEditor(it) },
            onLongClick = { schedule, itemView -> startScheduleDrag(schedule, itemView) },
            categoryName = { id -> categoryName(id) }
        )
        binding.inboxRecycler.layoutManager = LinearLayoutManager(this)
        binding.inboxRecycler.adapter = inboxAdapter

        binding.addButton.setOnClickListener { showScheduleEditor(null) }
        binding.categoryButton.setOnClickListener { showCategoryManager() }
        binding.weekHeader.headerOnly = true
        binding.weekHeader.onDayFocus = { focusDate(it) }
        binding.weekSchedule.onScheduleClick = { showScheduleDetail(it) }
        binding.weekSchedule.onDayFocus = { focusDate(it) }
        binding.weekSchedule.onScheduleDrop = { id, date, day, minutes ->
            placeSchedule(id, date, day, minutes)
        }
        binding.monthCalendar.onDateSelected = { focusDate(it) }
        binding.monthCalendar.onMonthChanged = {
            displayedMonth = it
            renderMainSurface()
        }
        binding.monthCalendar.onTodaySelected = {
            selectedDate = LocalDate.now()
            selectedWeekStart = weekStart(selectedDate)
            focusedDay = selectedDate.dayOfWeek.value
            displayedMonth = selectedDate.withDayOfMonth(1)
            renderMainSurface()
        }

        installGestures()

        lifecycleScope.launch { repository.seedDefaultsIfNeeded() }
        lifecycleScope.launch {
            combine(repository.schedules, repository.categories) { scheduleList, categoryList ->
                scheduleList to categoryList
            }.collect { (scheduleList, categoryList) ->
                schedules = scheduleList
                categories = categoryList
                renderInbox()
                renderMainSurface()
            }
        }
    }

    private fun installGestures() {
        gestureDetector = GestureDetectorCompat(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    if (calendarMode || abs(velocityX) < abs(velocityY)) return false
                    if (velocityX < -350) focusDate(dateForDay((focusedDay + 1).coerceAtMost(7)))
                    if (velocityX > 350) focusDate(dateForDay((focusedDay - 1).coerceAtLeast(1)))
                    return true
                }
            }
        )
        scaleDetector = android.view.ScaleGestureDetector(
            this,
            object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleEnd(detector: android.view.ScaleGestureDetector) {
                    if (detector.scaleFactor < 0.92f && !calendarMode) {
                        calendarMode = true
                        renderMainSurface()
                    } else if (detector.scaleFactor > 1.08f && calendarMode) {
                        calendarMode = false
                        renderMainSurface()
                    }
                }
            }
        )
        binding.contentContainer.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun renderMainSurface() {
        binding.scheduleSurface.animate().alpha(0.78f).setDuration(80).withEndAction {
            if (calendarMode) {
                binding.weekScroll.visibility = View.GONE
                binding.calendarScroll.visibility = View.VISIBLE
                renderMonthlyCalendar()
            } else {
                binding.calendarScroll.visibility = View.GONE
                binding.weekScroll.visibility = View.VISIBLE
                renderTimetable()
            }
            binding.scheduleSurface.animate().alpha(1f).setDuration(130).start()
        }.start()
    }

    private fun renderInbox() {
        val inboxItems = schedules.filter { it.status == ScheduleStatus.INBOX }
        binding.inboxTitle.text = "Inbox (${inboxItems.size})"
        inboxAdapter.submit(inboxItems)
    }

    private fun renderTimetable() {
        val weekEnd = selectedWeekStart.plusDays(6)
        binding.titleText.text = "${selectedWeekStart.format(shortDateFormatter)} - ${weekEnd.format(shortDateFormatter)}"
        binding.weekHeader.selectedWeekStart = selectedWeekStart
        binding.weekHeader.focusedDay = focusedDay
        binding.weekSchedule.schedules = schedules
        binding.weekSchedule.selectedWeekStart = selectedWeekStart
        binding.weekSchedule.focusedDay = focusedDay
    }

    private fun renderMonthlyCalendar() {
        binding.titleText.text = displayedMonth.format(monthFormatter)
        binding.monthCalendar.displayedMonth = displayedMonth
        binding.monthCalendar.selectedDate = selectedDate
    }

    private fun focusDate(date: LocalDate) {
        if (date == selectedDate && !calendarMode) return
        selectedDate = date
        selectedWeekStart = weekStart(date)
        focusedDay = date.dayOfWeek.value
        calendarMode = false
        renderMainSurface()
    }

    private fun startScheduleDrag(schedule: ScheduleEntity, itemView: View): Boolean {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        val data = ClipData.newPlainText("scheduleId", schedule.id.toString())
        val shadow = View.DragShadowBuilder(itemView)
        itemView.startDragAndDrop(data, shadow, schedule.id, 0)
        Toast.makeText(this, "Drop on a time slot.", Toast.LENGTH_SHORT).show()
        return true
    }

    private fun placeSchedule(id: Long, date: LocalDate, day: Int, slotStart: Int) {
        val schedule = schedules.firstOrNull { it.id == id } ?: return
        lifecycleScope.launch {
            repository.saveSchedule(
                schedule.copy(
                    status = ScheduleStatus.SCHEDULED,
                    scheduledDate = date.toEpochDay(),
                    dayOfWeek = day,
                    startTimeMinutes = slotStart
                )
            )
        }
        focusDate(date)
    }

    private fun showScheduleEditor(schedule: ScheduleEntity?) {
        if (categories.isEmpty()) {
            Toast.makeText(this, "Create a category first.", Toast.LENGTH_SHORT).show()
            return
        }

        val form = cardForm()
        val titleInput = EditText(this).apply {
            hint = "Title"
            setText(schedule?.title.orEmpty())
            setSingleLine(true)
        }
        val categorySpinner = Spinner(this)
        val colorSpinner = Spinner(this)
        val repeatModeSpinner = Spinner(this)
        val hourPicker = NumberPicker(this).apply {
            minValue = 0
            maxValue = 8
            value = (schedule?.durationMinutes ?: 0) / 60
            wrapSelectorWheel = false
        }
        val minuteValues = arrayOf("00", "15", "30", "45")
        val minutePicker = NumberPicker(this).apply {
            minValue = 0
            maxValue = minuteValues.lastIndex
            displayedValues = minuteValues
            value = when ((schedule?.durationMinutes ?: -1) % 60) {
                15 -> 1
                30 -> 2
                45 -> 3
                else -> 0
            }
            wrapSelectorWheel = false
        }
        var selectedDeadline: LocalDate? = schedule?.deadline?.let { dateFromEpochDay(it) }
        val deadlineInput = EditText(this).apply {
            hint = "YYYY.MM.DD"
            setText(selectedDeadline?.format(deadlineFormatter).orEmpty())
            setSingleLine(true)
            isFocusable = false
            isCursorVisible = false
            isClickable = true
            background = getDrawable(R.drawable.bg_date_input)
            setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_calendar_24, 0)
            compoundDrawablePadding = dp(10)
            setPadding(dp(12), 0, dp(12), 0)
            minHeight = dp(48)
            setOnClickListener {
                showDeadlinePicker(selectedDeadline) { date ->
                    selectedDeadline = date
                    setText(date?.format(deadlineFormatter).orEmpty())
                }
            }
        }

        categorySpinner.adapter = simpleAdapter(listOf("Unset") + categories.map { it.name })
        val selectedCategoryIndex = categories.indexOfFirst { it.id == schedule?.categoryId }.takeIf { it >= 0 }?.plus(1) ?: 0
        categorySpinner.setSelection(selectedCategoryIndex)

        colorSpinner.adapter = simpleAdapter(listOf("Unset") + colorOptions.map { it.first })
        val selectedColorIndex = colorOptions.indexOfFirst { it.second == schedule?.color }.takeIf { it >= 0 }?.plus(1) ?: 0
        colorSpinner.setSelection(selectedColorIndex)

        repeatModeSpinner.adapter = simpleAdapter(listOf("Unset", "One-time", "Weekly", "Daily"))
        repeatModeSpinner.setSelection(
            when (schedule?.repeatType) {
                null -> 0
                RepeatType.NONE -> 1
                RepeatType.WEEKLY -> 2
                RepeatType.DAILY -> 3
            }
        )

        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0) {
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
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        form.addView(label("Title"))
        form.addView(titleInput)
        form.addView(label("Category"))
        form.addView(categorySpinner)
        form.addView(label("Color"))
        form.addView(colorSpinner)
        form.addView(label("Repeat / One-time"))
        form.addView(repeatModeSpinner)
        form.addView(label("Duration"))
        form.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(hourPicker, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(minutePicker, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
        )
        form.addView(label("Deadline"))
        form.addView(deadlineInput)

        AlertDialog.Builder(this)
            .setTitle(if (schedule == null) "Add schedule" else "Edit schedule")
            .setView(scrollWrap(form))
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val title = titleInput.text.toString().trim()
                        if (title.isBlank()) {
                            titleInput.error = "Required"
                            return@setOnClickListener
                        }
                        val deadline = selectedDeadline?.toEpochDay()
                        val selectedCategory = categorySpinner.selectedItemPosition
                            .takeIf { it > 0 }
                            ?.let { categories[it - 1] }
                        val repeatType = when (repeatModeSpinner.selectedItemPosition) {
                            1 -> RepeatType.NONE
                            2 -> RepeatType.WEEKLY
                            3 -> RepeatType.DAILY
                            else -> null
                        }
                        val durationMinutes = ((hourPicker.value * 60) + minuteIndexToMinutes(minutePicker.value))
                            .takeIf { it > 0 }
                        val entity = ScheduleEntity(
                            id = schedule?.id ?: 0,
                            title = title,
                            categoryId = selectedCategory?.id,
                            color = colorSpinner.selectedItemPosition.takeIf { it > 0 }?.let { colorOptions[it - 1].second },
                            isRepeat = repeatType?.let { it != RepeatType.NONE },
                            repeatType = repeatType,
                            durationMinutes = durationMinutes,
                            deadline = deadline,
                            scheduledDate = schedule?.scheduledDate,
                            dayOfWeek = schedule?.dayOfWeek,
                            startTimeMinutes = schedule?.startTimeMinutes,
                            status = schedule?.status ?: ScheduleStatus.INBOX
                        )
                        lifecycleScope.launch { repository.saveSchedule(entity) }
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                        if (entity.status == ScheduleStatus.INBOX) animateIntoInbox()
                        dialog.dismiss()
                    }
                }
            }
            .show()
    }

    private fun showScheduleDetail(schedule: ScheduleEntity) {
        val card = cardForm()
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        var detailDone = schedule.status == ScheduleStatus.DONE
        val titleText = TextView(this).apply {
            text = schedule.title
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(24, 32, 42))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val doneButton = iconButton(R.drawable.ic_check_24, detailDone)
        val deleteButton = iconButton(R.drawable.ic_trash_24)
        val inboxButton = iconButton(R.drawable.ic_inbox_24)
        header.addView(titleText)
        header.addView(doneButton)
        header.addView(deleteButton)
        header.addView(inboxButton)
        card.addView(header)
        card.addView(detailLine("Category", categoryName(schedule.categoryId)))
        card.addView(detailLine("Date", schedule.scheduledDate?.let { dateFromEpochDay(it).format(dateFormatter) } ?: "Inbox"))
        card.addView(detailLine("Time", schedule.startTimeMinutes?.let { "${minutesToText(it)} - ${minutesToText(it + durationOrDefault(schedule))}" } ?: "Unassigned"))
        card.addView(detailLine("Duration", schedule.durationMinutes?.let { "$it minutes" } ?: "Unset"))
        card.addView(detailLine("Repeat", schedule.repeatType?.name ?: "Unset"))
        card.addView(detailLine("Deadline", schedule.deadline?.let { dateFromEpochDay(it).format(dateFormatter) } ?: "None"))

        val dialog = AlertDialog.Builder(this)
            .setView(card)
            .setNegativeButton("Close", null)
            .setPositiveButton("Edit") { _, _ -> showScheduleEditor(schedule) }
            .create()
        doneButton.setOnClickListener {
            detailDone = !detailDone
            lifecycleScope.launch { repository.markDone(schedule, detailDone) }
            doneButton.isSelected = detailDone
            doneButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (detailDone) Color.rgb(34, 108, 224) else Color.rgb(238, 241, 245)
            )
            doneButton.iconTint = android.content.res.ColorStateList.valueOf(
                if (detailDone) Color.WHITE else Color.rgb(102, 112, 133)
            )
            card.alpha = if (detailDone) 0.72f else 1f
        }
        inboxButton.setOnClickListener {
            lifecycleScope.launch { repository.moveToInbox(schedule) }
            animateIntoInbox()
            dialog.dismiss()
        }
        deleteButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete schedule?")
                .setMessage(schedule.title)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete") { _, _ ->
                    lifecycleScope.launch { repository.deleteSchedule(schedule) }
                    dialog.dismiss()
                }
                .show()
        }
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
        dialog.show()
    }

    private fun showCategoryManager() {
        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = CategoryAdapter(categories) { showCategoryEditor(it) }
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cellBackground(Color.WHITE, Color.TRANSPARENT, 14)
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(360)))
        }
        AlertDialog.Builder(this)
            .setTitle("Categories")
            .setView(container)
            .setNegativeButton("Close", null)
            .setPositiveButton("+") { _, _ -> showCategoryEditor(null) }
            .show()
    }

    private fun showCategoryEditor(category: CategoryEntity?) {
        val form = cardForm()
        val nameInput = EditText(this).apply {
            hint = "Name"
            setText(category?.name.orEmpty())
            setSingleLine(true)
        }
        val colorSpinner = Spinner(this).apply {
            adapter = simpleAdapter(listOf("Unset") + colorOptions.map { it.first })
            setSelection(colorOptions.indexOfFirst { it.second == category?.defaultColor }.takeIf { it >= 0 }?.plus(1) ?: 0)
        }
        val hourPicker = NumberPicker(this).apply {
            minValue = 0
            maxValue = 8
            value = (category?.defaultDurationMinutes ?: 0) / 60
            wrapSelectorWheel = false
        }
        val minuteValues = arrayOf("00", "15", "30", "45")
        val minutePicker = NumberPicker(this).apply {
            minValue = 0
            maxValue = minuteValues.lastIndex
            displayedValues = minuteValues
            value = ((category?.defaultDurationMinutes ?: 0) % 60) / 15
            wrapSelectorWheel = false
        }
        val repeatSpinner = Spinner(this).apply {
            adapter = simpleAdapter(listOf("Unset", "One-time", "Daily", "Weekly"))
            setSelection(
                when (category?.defaultRepeatType) {
                    null -> 0
                    RepeatType.NONE -> 1
                    RepeatType.DAILY -> 2
                    RepeatType.WEEKLY -> 3
                }
            )
        }

        form.addView(label("Name"))
        form.addView(nameInput)
        form.addView(label("Default color"))
        form.addView(colorSpinner)
        form.addView(label("Default duration"))
        form.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(hourPicker, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(minutePicker, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
        )
        form.addView(label("Default repeat"))
        form.addView(repeatSpinner)

        AlertDialog.Builder(this)
            .setTitle(if (category == null) "Add category" else "Edit category")
            .setView(form)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val name = nameInput.text.toString().trim()
                        if (name.isBlank()) {
                            nameInput.error = "Required"
                            return@setOnClickListener
                        }
                        lifecycleScope.launch {
                            repository.saveCategory(
                                CategoryEntity(
                                    id = category?.id ?: 0,
                                    name = name,
                                    defaultColor = colorSpinner.selectedItemPosition.takeIf { it > 0 }?.let { colorOptions[it - 1].second },
                                    defaultDurationMinutes = ((hourPicker.value * 60) + minuteIndexToMinutes(minutePicker.value)).takeIf { it > 0 },
                                    defaultRepeatType = when (repeatSpinner.selectedItemPosition) {
                                        1 -> RepeatType.NONE
                                        2 -> RepeatType.DAILY
                                        3 -> RepeatType.WEEKLY
                                        else -> null
                                    }
                                )
                            )
                        }
                        dialog.dismiss()
                    }
                }
            }
            .show()
    }

    private fun animateIntoInbox() {
        binding.inboxRecycler.scaleX = 0.96f
        binding.inboxRecycler.scaleY = 0.96f
        binding.inboxRecycler.alpha = 0.55f
        binding.inboxRecycler.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(220).start()
    }

    private fun simpleAdapter(values: List<String>): ArrayAdapter<String> {
        return ArrayAdapter(this, android.R.layout.simple_spinner_item, values).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun cardForm(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(12), dp(20), dp(12))
        background = cellBackground(Color.WHITE, Color.TRANSPARENT, 14)
    }

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        setPadding(0, dp(12), 0, dp(2))
        setTextColor(Color.rgb(102, 112, 133))
        textSize = 12f
    }

    private fun detailLine(label: String, value: String): TextView = TextView(this).apply {
        text = "$label: $value"
        setPadding(0, dp(8), 0, 0)
        setTextColor(Color.rgb(102, 112, 133))
        textSize = 14f
    }

    private fun iconButton(iconRes: Int, selected: Boolean = false): MaterialButton {
        return MaterialButton(this).apply {
            text = ""
            icon = getDrawable(iconRes)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconPadding = 0
            insetTop = 0
            insetBottom = 0
            minWidth = dp(42)
            minimumWidth = dp(42)
            width = dp(42)
            height = dp(42)
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                marginStart = dp(6)
            }
            cornerRadius = dp(8)
            backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (selected) Color.rgb(34, 108, 224) else Color.rgb(238, 241, 245)
            )
            iconTint = android.content.res.ColorStateList.valueOf(
                if (selected) Color.WHITE else Color.rgb(102, 112, 133)
            )
        }
    }

    private fun categoryName(categoryId: Long?): String {
        return categories.firstOrNull { it.id == categoryId }?.name ?: "No category"
    }

    private fun minutesToText(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    private fun dateForDay(day: Int): LocalDate = selectedWeekStart.plusDays((day - 1).toLong())

    private fun dateFromEpochDay(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    private fun durationOrDefault(schedule: ScheduleEntity): Int = schedule.durationMinutes ?: 60

    private fun minuteIndexToMinutes(index: Int): Int {
        return when (index) {
            1 -> 15
            2 -> 30
            3 -> 45
            else -> 0
        }
    }

    private fun scrollWrap(content: View): ScrollView = ScrollView(this).apply {
        addView(content)
    }

    private fun showDeadlinePicker(current: LocalDate?, onSelected: (LocalDate?) -> Unit) {
        val initial = current ?: LocalDate.now()
        val picker = DatePicker(this).apply {
            init(initial.year, initial.monthValue - 1, initial.dayOfMonth, null)
        }
        AlertDialog.Builder(this)
            .setTitle("Deadline")
            .setView(picker)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Clear") { _, _ -> onSelected(null) }
            .setPositiveButton("Set") { _, _ ->
                onSelected(LocalDate.of(picker.year, picker.month + 1, picker.dayOfMonth))
            }
            .show()
    }

    private fun cellBackground(fill: Int, stroke: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            color = android.content.res.ColorStateList.valueOf(fill)
            cornerRadius = dp(radiusDp).toFloat()
            if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private class ScheduleAdapter(
        private val onClick: (ScheduleEntity) -> Unit,
        private val onLongClick: (ScheduleEntity, View) -> Boolean,
        private val categoryName: (Long?) -> String
    ) : RecyclerView.Adapter<ScheduleAdapter.ScheduleViewHolder>() {
        private val items = mutableListOf<ScheduleEntity>()

        fun submit(newItems: List<ScheduleEntity>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduleViewHolder {
            val binding = ItemScheduleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ScheduleViewHolder(binding, onClick, onLongClick, categoryName)
        }

        override fun onBindViewHolder(holder: ScheduleViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        private class ScheduleViewHolder(
            private val binding: ItemScheduleBinding,
            private val onClick: (ScheduleEntity) -> Unit,
            private val onLongClick: (ScheduleEntity, View) -> Boolean,
            private val categoryName: (Long?) -> String
        ) : RecyclerView.ViewHolder(binding.root) {
            fun bind(schedule: ScheduleEntity) {
                binding.titleText.text = schedule.title
                binding.metaText.text = "${schedule.durationMinutes?.let { "$it min" } ?: "No duration"} · ${categoryName(schedule.categoryId)}"
                binding.colorStrip.background = GradientDrawable().apply {
                    color = android.content.res.ColorStateList.valueOf(schedule.color ?: Color.rgb(160, 166, 178))
                    cornerRadius = binding.root.resources.displayMetrics.density * 3
                }
                binding.root.setOnClickListener { onClick(schedule) }
                binding.root.setOnLongClickListener { onLongClick(schedule, binding.root) }
            }
        }
    }

    private class CategoryAdapter(
        private val items: List<CategoryEntity>,
        private val onClick: (CategoryEntity) -> Unit
    ) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
            val textView = TextView(parent.context).apply {
                setPadding(18, 16, 18, 16)
                textSize = 16f
                setTextColor(Color.rgb(24, 32, 42))
            }
            return CategoryViewHolder(textView, onClick)
        }

        override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        private class CategoryViewHolder(
            private val textView: TextView,
            private val onClick: (CategoryEntity) -> Unit
        ) : RecyclerView.ViewHolder(textView) {
            fun bind(category: CategoryEntity) {
                val duration = category.defaultDurationMinutes?.let { "$it min" } ?: "Unset duration"
                val repeat = category.defaultRepeatType?.name ?: "Unset repeat"
                textView.text = "${category.name}\n$duration · $repeat"
                textView.setOnClickListener { onClick(category) }
            }
        }
    }

    companion object {
        private fun weekStart(date: LocalDate): LocalDate {
            return date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        }
    }
}
