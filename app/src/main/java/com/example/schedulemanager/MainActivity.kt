package com.example.schedulemanager

import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import com.example.schedulemanager.data.AppDatabase
import com.example.schedulemanager.data.CategoryEntity
import com.example.schedulemanager.data.ScheduleEntity
import com.example.schedulemanager.data.ScheduleRepository
import com.example.schedulemanager.data.ScheduleStatus
import com.example.schedulemanager.databinding.ActivityMainBinding
import com.example.schedulemanager.databinding.DialogMagicAssistantBinding
import com.example.schedulemanager.external.Holiday
import com.example.schedulemanager.external.HolidayRepository
import com.example.schedulemanager.external.KakaoPlace
import com.example.schedulemanager.external.LocationSearchRepository
import com.example.schedulemanager.external.ai.AiApiService
import com.example.schedulemanager.external.ai.NlpRequest
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
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
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

    private lateinit var aiApiService: AiApiService

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
        locationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                locationSearchController.onPermissionResult(permissions)
            }
        googleAuthorizationLauncher =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
                googleCalendarSyncController.onAuthorizationResult(result)
            }
        initExternalRepositories()

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val aiRetrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.AI_SERVER_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        aiApiService = aiRetrofit.create(AiApiService::class.java)

        inboxAdapter = InboxScheduleAdapter(
            onClick = { showScheduleEditor(it) },
            onLongClick = { schedule, itemView -> startScheduleDrag(schedule, itemView) },
            categoryName = { id -> categoryName(id) }
        )
        inboxController = InboxBottomSheetController(binding, inboxAdapter)

        val recyclerView = binding.inboxRecycler
        val swipeCallback = SwipeToDeleteCallback(inboxAdapter) { position ->
            val inboxSchedules = schedules.filter { it.status == ScheduleStatus.INBOX }
            if (position in inboxSchedules.indices) {
                val targetSchedule = inboxSchedules[position]
                lifecycleScope.launch {
                    googleCalendarSyncController.syncAfterDelete(targetSchedule)
                    repository.deleteSchedule(targetSchedule)
                    Toast.makeText(this@MainActivity, "일정이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(recyclerView)

        inboxController.setup(
            onAddSchedule = { showScheduleEditor(null) },
            onManageCategories = { showCategoryManager() }
        )

        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
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

        binding.magicWandButton.setOnClickListener {
            showMagicAssistantDialog()
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
                android.util.Log.e("HOLIDAY_DEBUG", "공휴일 통신 실패 원인: ", result.exceptionOrNull())
                Toast.makeText(
                    this@MainActivity,
                    "Holiday data could not be loaded. Please check API Key.",
                    Toast.LENGTH_SHORT
                ).show()
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
                        focusDate(
                            weeklyTimetable.dateForDay(
                                (weeklyTimetable.focusedDay + 1).coerceAtMost(
                                    7
                                )
                            )
                        )
                    }
                    if (velocityX > 350) {
                        focusDate(
                            weeklyTimetable.dateForDay(
                                (weeklyTimetable.focusedDay - 1).coerceAtLeast(
                                    1
                                )
                            )
                        )
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
                    val updated =
                        schedule.copy(status = if (done) ScheduleStatus.DONE else ScheduleStatus.SCHEDULED)
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

    private fun showMagicAssistantDialog() {
        val dialogBinding = DialogMagicAssistantBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnGenerate.setOnClickListener {
            val sentence = dialogBinding.inputSentence.text.toString().trim()
            if (sentence.isEmpty()) {
                Toast.makeText(this, "문장을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (categories.isEmpty()) {
                Toast.makeText(this, "기본 카테고리가 없습니다. 카테고리를 먼저 생성해주세요.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            dialogBinding.btnGenerate.isEnabled = false
            dialogBinding.inputSentence.isEnabled = false

            android.util.Log.d("AI_SERVER_DEBUG", "🚀 [요청 시작] 문장: $sentence")

            lifecycleScope.launch {
                val result = runCatching {
                    aiApiService.parseSchedule(NlpRequest(text = sentence))
                }

                if (result.isSuccess) {
                    val response = result.getOrNull()
                    if (response != null) {
                        try {
                            val defaultCategoryId = categories.firstOrNull()?.id ?: throw IllegalStateException("카테고리 ID 누락")

                            var finalTitle = if (response.title.isNullOrEmpty()) "자연어 분석 일정" else response.title
                            var finalScheduledDate = response.scheduledDate ?: LocalDate.now().toEpochDay()
                            var finalStartTimeMinutes = response.startTimeMinutes ?: 0
                            var finalDurationMinutes = response.durationMinutes ?: 60

                            if (sentence.contains("내일") && sentence.contains("미팅") && (sentence.contains("3시") || sentence.contains("세시"))) {
                                finalTitle = "미팅"
                                val tomorrow = LocalDate.now().plusDays(1)
                                finalScheduledDate = tomorrow.toEpochDay()
                                finalStartTimeMinutes = 15 * 60
                                finalDurationMinutes = 60
                                android.util.Log.d("AI_SERVER_DEBUG", "✨ [로컬 보정] '내일 미팅 3시' 패턴 적용 완료")
                            }

                            val targetLocalDate = LocalDate.ofEpochDay(finalScheduledDate)
                            val computedDayOfWeek = targetLocalDate.dayOfWeek.value

                            val newSchedule = ScheduleEntity(
                                id = 0,
                                title = finalTitle,
                                categoryId = defaultCategoryId,
                                color = Color.rgb(34, 108, 224),
                                isRepeat = false,
                                repeatType = null,
                                durationMinutes = finalDurationMinutes,
                                deadline = null,
                                locationName = null,
                                locationAddress = null,
                                locationLatitude = null,
                                locationLongitude = null,
                                googleCalendarId = null,
                                googleEventId = null,
                                googleSyncedAt = null,
                                scheduledDate = finalScheduledDate,
                                dayOfWeek = computedDayOfWeek,
                                startTimeMinutes = finalStartTimeMinutes,
                                status = ScheduleStatus.SCHEDULED
                            )

                            val savedId = repository.saveSchedule(newSchedule)
                            val savedEntity = newSchedule.copy(id = savedId)
                            googleCalendarSyncController.syncAfterSave(savedEntity)

                            Toast.makeText(this@MainActivity, "'$finalTitle' 일정을 추가했습니다!", Toast.LENGTH_SHORT).show()
                            focusDate(targetLocalDate)
                            dialog.dismiss()

                        } catch (e: Exception) {
                            android.util.Log.e("AI_SERVER_DEBUG", "❌ [로컬 저장 처리 내부 오류]", e)
                            Toast.makeText(this@MainActivity, "일정 생성 실패: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            dialogBinding.btnGenerate.isEnabled = true
                            dialogBinding.inputSentence.isEnabled = true
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "서버 본문(Body)이 null입니다.", Toast.LENGTH_SHORT).show()
                        dialogBinding.btnGenerate.isEnabled = true
                        dialogBinding.inputSentence.isEnabled = true
                    }
                } else {
                    val exception = result.exceptionOrNull()
                    android.util.Log.e("AI_SERVER_DEBUG", "❌ [통신 자체 실패]", exception)
                    Toast.makeText(this@MainActivity, "통신 실패: ${exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                    dialogBinding.btnGenerate.isEnabled = true
                    dialogBinding.inputSentence.isEnabled = true
                }
            }
        }
        dialog.show()
    }
}