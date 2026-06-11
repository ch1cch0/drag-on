package com.example.schedulemanager

import android.app.AlertDialog
import android.content.ClipData
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.schedulemanager.data.AppDatabase
import com.example.schedulemanager.data.CategoryEntity
import com.example.schedulemanager.data.ScheduleEntity
import com.example.schedulemanager.data.ScheduleRepository
import com.example.schedulemanager.data.ScheduleStatus
import com.example.schedulemanager.databinding.ActivityMainBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

class MainActivity : AppCompatActivity(), MonthCalendarFragment.Callbacks {
    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: ScheduleRepository
    private lateinit var inboxAdapter: InboxScheduleAdapter
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>
    private lateinit var gestureDetector: GestureDetectorCompat
    private lateinit var scaleDetector: android.view.ScaleGestureDetector
    private lateinit var locationClient: FusedLocationProviderClient
    private lateinit var locationPermissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var monthCalendarFragment: MonthCalendarFragment
    private lateinit var weeklyTimetable: WeeklyTimetableController
    private lateinit var locationSearchController: LocationSearchController

    private lateinit var holidayRepository: HolidayRepository
    private lateinit var locationSearchRepository: LocationSearchRepository

    private var schedules: List<ScheduleEntity> = emptyList()
    private var categories: List<CategoryEntity> = emptyList()
    private var displayedMonth: LocalDate = LocalDate.now().withDayOfMonth(1)
    private var calendarMode = false
    private var cumulativeScale = 1f

    private var currentHolidays: List<Holiday> = emptyList()

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
        locationClient = LocationServices.getFusedLocationProviderClient(this)
        locationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            locationSearchController.onPermissionResult(permissions)
        }
        initExternalRepositories()

        bottomSheetBehavior = BottomSheetBehavior.from(binding.inboxSheet)
        val contentBasePaddingBottom = binding.contentContainer.paddingBottom
        val inboxBasePaddingBottom = binding.inboxSheet.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            binding.contentContainer.setPadding(
                binding.contentContainer.paddingLeft,
                binding.contentContainer.paddingTop,
                binding.contentContainer.paddingRight,
                contentBasePaddingBottom + systemBars.bottom
            )
            binding.inboxSheet.setPadding(
                binding.inboxSheet.paddingLeft,
                binding.inboxSheet.paddingTop,
                binding.inboxSheet.paddingRight,
                inboxBasePaddingBottom + systemBars.bottom
            )
            binding.inboxSheet.post {
                bottomSheetBehavior.peekHeight = dp(92) + systemBars.bottom
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
            insets
        }
        inboxAdapter = InboxScheduleAdapter(
            onClick = { showScheduleEditor(it) },
            onLongClick = { schedule, itemView -> startScheduleDrag(schedule, itemView) },
            categoryName = { id -> categoryName(id) }
        )
        binding.inboxRecycler.layoutManager = LinearLayoutManager(this)
        binding.inboxRecycler.adapter = inboxAdapter

        binding.addButton.setOnClickListener { showScheduleEditor(null) }
        binding.categoryButton.setOnClickListener { showCategoryManager() }
        weeklyTimetable = WeeklyTimetableController(
            binding = binding,
            titleFormatter = shortDateFormatter,
            onScheduleClick = { showScheduleDetail(it) },
            onScheduleDrop = { id, date, day, minutes -> placeSchedule(id, date, day, minutes) },
            onPinch = { handlePinchScale(it) },
            onFocusChanged = { focusDate(it) }
        )
        monthCalendarFragment = MonthCalendarFragment()
        supportFragmentManager.commit {
            replace(binding.monthFragmentContainer.id, monthCalendarFragment)
        }

        installGestures()

        lifecycleScope.launch { repository.seedDefaultsIfNeeded() }
        lifecycleScope.launch {
            combine(repository.schedules, repository.categories) { scheduleList, categoryList ->
                scheduleList to categoryList
            }.collect { (scheduleList, categoryList) ->
                schedules = scheduleList
                categories = categoryList
                weeklyTimetable.schedules = scheduleList
                renderInbox()
                renderMainSurface()
            }
        }

        fetchHolidaysForMonth(displayedMonth)
    }

    private fun initExternalRepositories() {
        holidayRepository = HolidayRepository(BuildConfig.GO_DATA_API_KEY)
        locationSearchRepository = LocationSearchRepository(BuildConfig.KAKAO_REST_API_KEY)
        locationSearchController = LocationSearchController(
            activity = this,
            lifecycleScope = lifecycleScope,
            locationClient = locationClient,
            locationSearchRepository = locationSearchRepository,
            permissionLauncher = locationPermissionLauncher
        )
    }

    private fun fetchHolidaysForMonth(date: LocalDate) {
        lifecycleScope.launch {
            val result = runCatching { holidayRepository.holidaysForMonth(date) }
            if (result.isSuccess) {
                currentHolidays = result.getOrDefault(emptyList())
                weeklyTimetable.holidays = currentHolidays
                renderMainSurface()
            } else {
                currentHolidays = emptyList()
                weeklyTimetable.holidays = currentHolidays
                renderMainSurface()
                Toast.makeText(this@MainActivity, "Holiday data could not be loaded.", Toast.LENGTH_SHORT).show()
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
                    if (velocityX < -350) {
                        focusDate(weeklyTimetable.dateForDay((weeklyTimetable.focusedDay + 1).coerceAtMost(7)))
                    }
                    if (velocityX > 350) {
                        focusDate(weeklyTimetable.dateForDay((weeklyTimetable.focusedDay - 1).coerceAtLeast(1)))
                    }
                    return true
                }
            }
        )
        scaleDetector = android.view.ScaleGestureDetector(
            this,
            object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: android.view.ScaleGestureDetector): Boolean {
                    cumulativeScale = 1f
                    return true
                }

                override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
                    cumulativeScale *= detector.scaleFactor
                    return true
                }

                override fun onScaleEnd(detector: android.view.ScaleGestureDetector) {
                    handlePinchScale(cumulativeScale)
                }
            }
        )
        binding.contentContainer.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun handlePinchScale(scaleFactor: Float) {
        if (scaleFactor < 0.92f && !calendarMode) {
            calendarMode = true
            renderMainSurface()
        } else if (scaleFactor > 1.08f && calendarMode) {
            calendarMode = false
            renderMainSurface()
        }
    }

    private fun setInboxHidden(hidden: Boolean) {
        binding.inboxSheet.alpha = if (hidden) 0f else 1f
        binding.inboxSheet.isEnabled = !hidden
        binding.inboxSheet.isClickable = !hidden
        binding.inboxSheet.importantForAccessibility = if (hidden) {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
        bottomSheetBehavior.isDraggable = !hidden
        if (!hidden) {
            binding.inboxSheet.post {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }
    }

    private fun renderMainSurface() {
        binding.scheduleSurface.animate().alpha(0.78f).setDuration(80).withEndAction {
            if (calendarMode) {
                binding.titleText.visibility = View.GONE
                binding.weekScroll.visibility = View.GONE
                binding.monthFragmentContainer.visibility = View.VISIBLE
                setInboxHidden(true)
                renderMonthlyCalendar()
            } else {
                binding.titleText.visibility = View.VISIBLE
                binding.monthFragmentContainer.visibility = View.GONE
                setInboxHidden(false)
                binding.weekScroll.visibility = View.VISIBLE
                weeklyTimetable.render()
            }
            binding.scheduleSurface.animate().alpha(1f).setDuration(130).start()
        }.start()
    }

    private fun renderInbox() {
        val inboxItems = schedules.filter { it.status == ScheduleStatus.INBOX }
        binding.inboxTitle.text = "Inbox (${inboxItems.size})"
        inboxAdapter.submit(inboxItems)
    }

    private fun renderMonthlyCalendar() {
        monthCalendarFragment.displayedMonth = displayedMonth
        monthCalendarFragment.selectedDate = weeklyTimetable.selectedDate
        monthCalendarFragment.holidays = currentHolidays
    }

    private fun showMonthPicker(currentMonth: LocalDate) {
        val minYear = currentMonth.year - 50
        val maxYear = currentMonth.year + 50
        val yearPicker = NumberPicker(this).apply {
            minValue = minYear
            maxValue = maxYear
            displayedValues = (minYear..maxYear).map { "${it}년" }.toTypedArray()
            value = currentMonth.year
            wrapSelectorWheel = false
        }
        val monthPicker = NumberPicker(this).apply {
            minValue = 1
            maxValue = 12
            displayedValues = (1..12).map { "${it}월" }.toTypedArray()
            value = currentMonth.monthValue
            wrapSelectorWheel = true
        }
        val pickerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), 0)
            addView(yearPicker, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(monthPicker, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        AlertDialog.Builder(this)
            .setView(cardForm().apply { addView(pickerRow) })
            .setNegativeButton("Cancel", null)
            .setPositiveButton("OK") { _, _ ->
                displayedMonth = LocalDate.of(yearPicker.value, monthPicker.value, 1)
                fetchHolidaysForMonth(displayedMonth)
                renderMainSurface()
            }
            .show()
    }

    private fun focusDate(date: LocalDate) {
        if (date == weeklyTimetable.selectedDate && !calendarMode) return
        weeklyTimetable.focusDate(date)
        calendarMode = false
        if (weeklyTimetable.isWeekVisible()) {
            weeklyTimetable.render()
        } else {
            renderMainSurface()
        }
    }

    override fun onMonthDateSelected(date: LocalDate) {
        focusDate(date)
    }

    override fun onMonthChanged(month: LocalDate) {
        val previousMonth = displayedMonth
        displayedMonth = month
        if (previousMonth.year != displayedMonth.year || previousMonth.monthValue != displayedMonth.monthValue) {
            fetchHolidaysForMonth(displayedMonth)
        }
        if (calendarMode) {
            renderMonthlyCalendar()
        } else {
            renderMainSurface()
        }
    }

    override fun onMonthTodaySelected() {
        weeklyTimetable.focusToday()
        displayedMonth = weeklyTimetable.selectedDate.withDayOfMonth(1)
        fetchHolidaysForMonth(displayedMonth)
        renderMainSurface()
    }

    override fun onMonthTitleSelected(month: LocalDate) {
        showMonthPicker(month)
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
        ScheduleEditorDialog(
            context = this,
            categories = categories,
            colorOptions = colorOptions,
            schedule = schedule,
            onLocationSearch = ::showLocationSearch,
            onSave = { entity ->
                lifecycleScope.launch { repository.saveSchedule(entity) }
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                if (entity.status == ScheduleStatus.INBOX) animateIntoInbox()
            }
        ).show()
    }

    private fun showLocationSearch(query: String, onSelected: (KakaoPlace) -> Unit) {
        locationSearchController.search(query, onSelected)
    }

    private fun showScheduleDetail(schedule: ScheduleEntity) {
        ScheduleDetailDialog(
            context = this,
            schedule = schedule,
            categoryName = categoryName(schedule.categoryId),
            onDoneChanged = { done -> lifecycleScope.launch { repository.markDone(schedule, done) } },
            onMoveToInbox = {
                lifecycleScope.launch { repository.moveToInbox(schedule) }
                animateIntoInbox()
            },
            onDelete = { lifecycleScope.launch { repository.deleteSchedule(schedule) } },
            onEdit = { showScheduleEditor(schedule) }
        ).show()
    }

    private fun showCategoryManager() {
        CategoryManagerDialog(
            context = this,
            categories = categories,
            onCategorySelected = { showCategoryEditor(it) },
            onAddCategory = { showCategoryEditor(null) }
        ).show()
    }

    private fun showCategoryEditor(category: CategoryEntity?) {
        CategoryEditorDialog(
            context = this,
            category = category,
            colorOptions = colorOptions,
            onSave = { entity -> lifecycleScope.launch { repository.saveCategory(entity) } }
        ).show()
    }

    private fun animateIntoInbox() {
        binding.inboxRecycler.scaleX = 0.96f
        binding.inboxRecycler.scaleY = 0.96f
        binding.inboxRecycler.alpha = 0.55f
        binding.inboxRecycler.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(220).start()
    }

    private fun cardForm(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(12), dp(20), dp(12))
        background = cellBackground(Color.WHITE, Color.TRANSPARENT, 14)
    }

    private fun categoryName(categoryId: Long?): String {
        return categories.firstOrNull { it.id == categoryId }?.name ?: "No category"
    }

    private fun cellBackground(fill: Int, stroke: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            color = android.content.res.ColorStateList.valueOf(fill)
            cornerRadius = dp(radiusDp).toFloat()
            if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

}
