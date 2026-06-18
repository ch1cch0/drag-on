package com.example.schedulemanager

import android.content.ClipData
import android.graphics.Color
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.example.schedulemanager.data.AppDatabase
import com.example.schedulemanager.data.CategoryEntity
import com.example.schedulemanager.data.ScheduleEntity
import com.example.schedulemanager.data.ScheduleRepository
import com.example.schedulemanager.data.ScheduleStatus
import com.example.schedulemanager.databinding.ActivityMainBinding
import com.example.schedulemanager.external.Holiday
import com.example.schedulemanager.external.HolidayRepository
import com.example.schedulemanager.external.KakaoPlace
import com.example.schedulemanager.external.LocationSearchRepository
import com.example.schedulemanager.external.google.GoogleCalendarRepository
import com.example.schedulemanager.ui.category.CategoryEditorDialog
import com.example.schedulemanager.ui.category.CategoryManagerDialog
import com.example.schedulemanager.ui.google.GoogleCalendarSyncController
import com.example.schedulemanager.ui.inbox.InboxBottomSheetController
import com.example.schedulemanager.ui.inbox.InboxScheduleAdapter
import com.example.schedulemanager.ui.location.LocationSearchController
import com.example.schedulemanager.ui.month.MonthCalendarFragment
import com.example.schedulemanager.ui.month.MonthPickerDialog
import com.example.schedulemanager.ui.schedule.ScheduleDetailDialog
import com.example.schedulemanager.ui.schedule.ScheduleEditorDialog
import com.example.schedulemanager.ui.week.MainSurfaceController
import com.example.schedulemanager.ui.week.WeeklyTimetableController
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
    private lateinit var inboxController: InboxBottomSheetController
    private lateinit var gestureDetector: GestureDetectorCompat
    private lateinit var scaleDetector: android.view.ScaleGestureDetector
    private lateinit var locationClient: FusedLocationProviderClient
    private lateinit var locationPermissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var googleAuthorizationLauncher: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var monthCalendarFragment: MonthCalendarFragment
    private lateinit var weeklyTimetable: WeeklyTimetableController
    private lateinit var mainSurfaceController: MainSurfaceController
    private lateinit var locationSearchController: LocationSearchController
    private lateinit var googleCalendarSyncController: GoogleCalendarSyncController

    private lateinit var holidayRepository: HolidayRepository
    private lateinit var locationSearchRepository: LocationSearchRepository
    private lateinit var googleCalendarRepository: GoogleCalendarRepository

    private var schedules: List<ScheduleEntity> = emptyList()
    private var categories: List<CategoryEntity> = emptyList()
    private var displayedMonth: LocalDate = LocalDate.now().withDayOfMonth(1)
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
        googleAuthorizationLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            googleCalendarSyncController.onAuthorizationResult(result)
        }
        initExternalRepositories()

        inboxAdapter = InboxScheduleAdapter(
            onClick = { showScheduleEditor(it) },
            onLongClick = { schedule, itemView -> startScheduleDrag(schedule, itemView) },
            categoryName = { id -> categoryName(id) }
        )
        inboxController = InboxBottomSheetController(binding, inboxAdapter)
        inboxController.setup(
            onAddSchedule = { showScheduleEditor(null) },
            onManageCategories = { showCategoryManager() }
        )
        binding.googleCalendarButton.setOnClickListener {
            googleCalendarSyncController.showSyncDialog(schedules)
        }
        weeklyTimetable = WeeklyTimetableController(
            binding = binding,
            titleFormatter = shortDateFormatter,
            onScheduleClick = { showScheduleDetail(it) },
            onScheduleDrop = { id, date, day, minutes -> placeSchedule(id, date, day, minutes) },
            onPinch = { mainSurfaceController.handlePinchScale(it) },
            onFocusChanged = { focusDate(it) }
        )
        mainSurfaceController = MainSurfaceController(
            binding = binding,
            inboxController = inboxController,
            weeklyTimetable = weeklyTimetable,
            onRenderMonthlyCalendar = { renderMonthlyCalendar() }
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
                inboxController.render(scheduleList)
                mainSurfaceController.render()
            }
        }

        fetchHolidaysForMonth(displayedMonth)
    }

    private fun initExternalRepositories() {
        holidayRepository = HolidayRepository(BuildConfig.GO_DATA_API_KEY)
        locationSearchRepository = LocationSearchRepository(BuildConfig.KAKAO_REST_API_KEY)
        googleCalendarRepository = GoogleCalendarRepository()
        locationSearchController = LocationSearchController(
            activity = this,
            lifecycleScope = lifecycleScope,
            locationClient = locationClient,
            locationSearchRepository = locationSearchRepository,
            permissionLauncher = locationPermissionLauncher
        )
        googleCalendarSyncController = GoogleCalendarSyncController(
            activity = this,
            lifecycleScope = lifecycleScope,
            repository = repository,
            calendarRepository = googleCalendarRepository,
            authorizationLauncher = googleAuthorizationLauncher
        )
    }

    private fun fetchHolidaysForMonth(date: LocalDate) {
        lifecycleScope.launch {
            val result = runCatching { holidayRepository.holidaysForMonth(date) }
            if (result.isSuccess) {
                currentHolidays = result.getOrDefault(emptyList())
                weeklyTimetable.holidays = currentHolidays
                mainSurfaceController.render()
            } else {
                currentHolidays = emptyList()
                weeklyTimetable.holidays = currentHolidays
                mainSurfaceController.render()
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
                    if (mainSurfaceController.calendarMode || abs(velocityX) < abs(velocityY)) return false
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
                    mainSurfaceController.handlePinchScale(cumulativeScale)
                }
            }
        )
        binding.contentContainer.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun renderMonthlyCalendar() {
        monthCalendarFragment.displayedMonth = displayedMonth
        monthCalendarFragment.selectedDate = weeklyTimetable.selectedDate
        monthCalendarFragment.holidays = currentHolidays
    }

    private fun showMonthPicker(currentMonth: LocalDate) {
        MonthPickerDialog(this, currentMonth) { selectedMonth ->
            displayedMonth = selectedMonth
            fetchHolidaysForMonth(displayedMonth)
            mainSurfaceController.render()
        }.show()
    }

    private fun focusDate(date: LocalDate) {
        if (date == weeklyTimetable.selectedDate && !mainSurfaceController.calendarMode) return
        weeklyTimetable.focusDate(date)
        mainSurfaceController.showWeek()
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
        if (mainSurfaceController.calendarMode) {
            renderMonthlyCalendar()
        } else {
            mainSurfaceController.render()
        }
    }

    override fun onMonthTodaySelected() {
        weeklyTimetable.focusToday()
        displayedMonth = weeklyTimetable.selectedDate.withDayOfMonth(1)
        fetchHolidaysForMonth(displayedMonth)
        mainSurfaceController.render()
    }

    override fun onMonthTitleSelected(month: LocalDate) {
        showMonthPicker(month)
    }

    private fun startScheduleDrag(schedule: ScheduleEntity, itemView: View): Boolean {
        inboxController.collapse()
        val data = ClipData.newPlainText("scheduleId", schedule.id.toString())
        val shadow = View.DragShadowBuilder(itemView)
        itemView.startDragAndDrop(data, shadow, schedule.id, 0)
        Toast.makeText(this, "Drop on a time slot.", Toast.LENGTH_SHORT).show()
        return true
    }

    private fun placeSchedule(id: Long, date: LocalDate, day: Int, slotStart: Int) {
        val schedule = schedules.firstOrNull { it.id == id } ?: return
        val placedSchedule = schedule.copy(
            status = ScheduleStatus.SCHEDULED,
            scheduledDate = date.toEpochDay(),
            dayOfWeek = day,
            startTimeMinutes = slotStart
        )
        lifecycleScope.launch {
            repository.saveSchedule(placedSchedule)
            googleCalendarSyncController.syncAfterSave(placedSchedule)
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
                lifecycleScope.launch {
                    val id = repository.saveSchedule(entity)
                    val savedEntity = entity.copy(id = id)
                    googleCalendarSyncController.syncAfterSave(savedEntity)
                }
                inboxController.collapse()
                if (entity.status == ScheduleStatus.INBOX) inboxController.animateIntoInbox()
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
            onDoneChanged = { done ->
                lifecycleScope.launch {
                    val updated = schedule.copy(status = if (done) ScheduleStatus.DONE else ScheduleStatus.SCHEDULED)
                    repository.saveSchedule(updated)
                    googleCalendarSyncController.syncAfterSave(updated)
                }
            },
            onMoveToInbox = {
                lifecycleScope.launch {
                    googleCalendarSyncController.syncAfterDelete(schedule)
                    repository.moveToInbox(schedule)
                }
                inboxController.animateIntoInbox()
            },
            onDelete = {
                lifecycleScope.launch {
                    googleCalendarSyncController.syncAfterDelete(schedule)
                    repository.deleteSchedule(schedule)
                }
            },
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

    private fun categoryName(categoryId: Long?): String {
        return categories.firstOrNull { it.id == categoryId }?.name ?: "No category"
    }

}
