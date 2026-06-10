package com.example.schedulemanager

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.commit
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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.coroutines.resume

class MainActivity : AppCompatActivity(), MonthCalendarFragment.Callbacks {
    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: ScheduleRepository
    private lateinit var inboxAdapter: ScheduleAdapter
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>
    private lateinit var gestureDetector: GestureDetectorCompat
    private lateinit var scaleDetector: android.view.ScaleGestureDetector
    private lateinit var locationClient: FusedLocationProviderClient
    private lateinit var locationPermissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var monthCalendarFragment: MonthCalendarFragment
    private var pendingLocationSearch: PendingLocationSearch? = null

    private lateinit var holidayRepository: HolidayRepository
    private lateinit var locationSearchRepository: LocationSearchRepository

    private var schedules: List<ScheduleEntity> = emptyList()
    private var categories: List<CategoryEntity> = emptyList()
    private var selectedDate: LocalDate = LocalDate.now()
    private var selectedWeekStart: LocalDate = weekStart(LocalDate.now())
    private var focusedDay = LocalDate.now().dayOfWeek.value
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
            val pending = pendingLocationSearch ?: return@registerForActivityResult
            pendingLocationSearch = null
            if (permissions.values.any { it }) {
                searchLocationsWithCurrentPosition(pending.query, pending.onSelected)
            } else {
                Toast.makeText(this, "Searching without current location.", Toast.LENGTH_SHORT).show()
                searchLocations(pending.query, pending.onSelected, null)
            }
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
        binding.weekHeader.onPinch = { handlePinchScale(it) }
        binding.weekHeader.onFocusPositionChanged = { syncFocusPosition(it) }
        binding.weekSchedule.onScheduleClick = { showScheduleDetail(it) }
        binding.weekSchedule.onDayFocus = { focusDate(it) }
        binding.weekSchedule.onPinch = { handlePinchScale(it) }
        binding.weekSchedule.onFocusPositionChanged = { syncFocusPosition(it) }
        binding.weekSchedule.onScheduleDrop = { id, date, day, minutes ->
            placeSchedule(id, date, day, minutes)
        }
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
                renderInbox()
                renderMainSurface()
            }
        }

        fetchHolidaysForMonth(displayedMonth)
    }

    private fun initExternalRepositories() {
        holidayRepository = HolidayRepository(BuildConfig.GO_DATA_API_KEY)
        locationSearchRepository = LocationSearchRepository(BuildConfig.KAKAO_REST_API_KEY)
    }

    private fun fetchHolidaysForMonth(date: LocalDate) {
        lifecycleScope.launch {
            val result = runCatching { holidayRepository.holidaysForMonth(date) }
            if (result.isSuccess) {
                currentHolidays = result.getOrDefault(emptyList())
                renderMainSurface()
            } else {
                currentHolidays = emptyList()
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
                    if (velocityX < -350) focusDate(dateForDay((focusedDay + 1).coerceAtMost(7)))
                    if (velocityX > 350) focusDate(dateForDay((focusedDay - 1).coerceAtLeast(1)))
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

    private fun syncFocusPosition(position: Float?) {
        if (position != null) {
            binding.weekHeader.prepareFocusSettle(position)
            binding.weekSchedule.prepareFocusSettle(position)
        }
        binding.weekHeader.interactiveFocusPosition = position
        binding.weekSchedule.interactiveFocusPosition = position
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

        // ◀ [공휴일 엔진 복구] 주간 상단 컴포넌트에 공휴일 데이터 바인딩 주입 완료
        binding.weekHeader.holidays = currentHolidays

        binding.weekSchedule.schedules = schedules
        binding.weekSchedule.selectedWeekStart = selectedWeekStart
        binding.weekSchedule.focusedDay = focusedDay
        binding.weekSchedule.holidays = currentHolidays
    }

    private fun renderMonthlyCalendar() {
        monthCalendarFragment.displayedMonth = displayedMonth
        monthCalendarFragment.selectedDate = selectedDate
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
        if (date == selectedDate && !calendarMode) return
        selectedDate = date
        selectedWeekStart = weekStart(date)
        focusedDay = date.dayOfWeek.value
        calendarMode = false
        if (binding.weekScroll.visibility == View.VISIBLE && binding.monthFragmentContainer.visibility != View.VISIBLE) {
            renderTimetable()
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
        selectedDate = LocalDate.now()
        selectedWeekStart = weekStart(selectedDate)
        focusedDay = selectedDate.dayOfWeek.value
        displayedMonth = selectedDate.withDayOfMonth(1)
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
        if (query.isBlank()) {
            Toast.makeText(this, "Enter a location first.", Toast.LENGTH_SHORT).show()
            return
        }
        if (BuildConfig.KAKAO_REST_API_KEY.isBlank()) {
            Toast.makeText(this, "Kakao REST API key is missing.", Toast.LENGTH_SHORT).show()
            return
        }
        if (hasLocationPermission()) {
            searchLocationsWithCurrentPosition(query, onSelected)
            return
        }
        pendingLocationSearch = PendingLocationSearch(query, onSelected)
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun searchLocationsWithCurrentPosition(query: String, onSelected: (KakaoPlace) -> Unit) {
        lifecycleScope.launch {
            val location = currentLocationOrNull()
            if (location == null) {
                Toast.makeText(this@MainActivity, "Searching without current location.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "Searching near current location.", Toast.LENGTH_SHORT).show()
            }
            searchLocations(query, onSelected, location)
        }
    }

    private fun searchLocations(query: String, onSelected: (KakaoPlace) -> Unit, location: Location?) {
        lifecycleScope.launch {
            val result = runCatching { locationSearchRepository.search(query, location) }
            val places = result.getOrNull().orEmpty()
            if (result.isFailure) {
                Toast.makeText(this@MainActivity, "Location search failed.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (places.isEmpty()) {
                Toast.makeText(this@MainActivity, "No locations found.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            lateinit var dialog: AlertDialog
            val list = locationResultList(places) { place ->
                onSelected(place)
                dialog.dismiss()
            }
            dialog = AlertDialog.Builder(this@MainActivity)
                .setTitle("Select location")
                .setView(list)
                .setNegativeButton("Cancel", null)
                .create()
            dialog.setOnShowListener {
                list.layoutParams = list.layoutParams.apply {
                    height = dp(260)
                }
                list.requestLayout()
            }
            dialog.show()
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun currentLocationOrNull(): Location? {
        if (!hasLocationPermission()) {
            return null
        }
        return withTimeoutOrNull(1200) {
            cachedOrCurrentLocation()
        }
    }

    private suspend fun cachedOrCurrentLocation(): Location? = suspendCancellableCoroutine { continuation ->
        try {
            locationClient.lastLocation
                .addOnSuccessListener { cachedLocation ->
                    if (!continuation.isActive) return@addOnSuccessListener
                    if (cachedLocation != null) {
                        continuation.resume(cachedLocation)
                    } else {
                        val tokenSource = CancellationTokenSource()
                        continuation.invokeOnCancellation { tokenSource.cancel() }
                        locationClient
                            .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, tokenSource.token)
                            .addOnSuccessListener {
                                if (continuation.isActive) continuation.resume(it)
                            }
                            .addOnFailureListener {
                                if (continuation.isActive) continuation.resume(null)
                            }
                    }
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(null)
                }
        } catch (_: SecurityException) {
            if (continuation.isActive) continuation.resume(null)
        }
    }

    private fun locationResultList(places: List<KakaoPlace>, onSelected: (KakaoPlace) -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = dp(260)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260))
            addView(
                RecyclerView(this@MainActivity).apply {
                    layoutManager = LinearLayoutManager(this@MainActivity)
                    adapter = LocationAdapter(places, onSelected)
                    overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                    isNestedScrollingEnabled = true
                    setPadding(0, dp(4), 0, dp(4))
                    addItemDecoration(
                        object : RecyclerView.ItemDecoration() {
                            override fun onDrawOver(canvas: android.graphics.Canvas, parent: RecyclerView, state: RecyclerView.State) {
                                val paint = android.graphics.Paint().apply { color = Color.rgb(226, 231, 238) }
                                for (index in 0 until parent.childCount - 1) {
                                    val child = parent.getChildAt(index)
                                    val y = child.bottom.toFloat()
                                    canvas.drawRect(parent.paddingLeft.toFloat(), y, (parent.width - parent.paddingRight).toFloat(), y + dp(1), paint)
                                }
                            }
                        }
                    )
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260))
            )
        }
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

    private fun categoryName(categoryId: Long?): String {
        return categories.firstOrNull { it.id == categoryId }?.name ?: "No category"
    }

    private fun dateForDay(day: Int): LocalDate = selectedWeekStart.plusDays((day - 1).toLong())

    private fun minuteIndexToMinutes(index: Int): Int {
        return when (index) {
            1 -> 15
            2 -> 30
            3 -> 45
            else -> 0
        }
    }

    private fun cellBackground(fill: Int, stroke: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            color = android.content.res.ColorStateList.valueOf(fill)
            cornerRadius = dp(radiusDp).toFloat()
            if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class PendingLocationSearch(
        val query: String,
        val onSelected: (KakaoPlace) -> Unit
    )

    private class LocationAdapter(
        private val items: List<KakaoPlace>,
        private val onClick: (KakaoPlace) -> Unit
    ) : RecyclerView.Adapter<LocationAdapter.LocationViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocationViewHolder {
            val density = parent.resources.displayMetrics.density
            val container = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((18 * density).toInt(), (12 * density).toInt(), (18 * density).toInt(), (12 * density).toInt())
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            val nameText = TextView(parent.context).apply {
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.rgb(24, 32, 42))
            }
            val addressText = TextView(parent.context).apply {
                textSize = 12f
                setTextColor(Color.rgb(112, 124, 140))
                setPadding(0, (3 * density).toInt(), 0, 0)
            }
            container.addView(nameText)
            container.addView(addressText)
            return LocationViewHolder(container, nameText, addressText, onClick)
        }

        override fun onBindViewHolder(holder: LocationViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        private class LocationViewHolder(
            itemView: View,
            private val nameText: TextView,
            private val addressText: TextView,
            private val onClick: (KakaoPlace) -> Unit
        ) : RecyclerView.ViewHolder(itemView) {
            fun bind(place: KakaoPlace) {
                nameText.text = place.name
                addressText.text = place.metaText
                itemView.setOnClickListener { onClick(place) }
            }
        }
    }

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

        private inner class ScheduleViewHolder(
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
