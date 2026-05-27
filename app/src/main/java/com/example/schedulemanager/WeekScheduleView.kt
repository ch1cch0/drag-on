package com.example.schedulemanager

import android.animation.ValueAnimator
import android.content.ClipData
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.util.AttributeSet
import android.view.DragEvent
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.core.view.GestureDetectorCompat
import com.example.schedulemanager.data.RepeatType
import com.example.schedulemanager.data.ScheduleEntity
import com.example.schedulemanager.data.ScheduleStatus
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

class WeekScheduleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    var schedules: List<ScheduleEntity> = emptyList()
        set(value) {
            field = value
            invalidate()
        }
    var selectedWeekStart: LocalDate = weekStart(LocalDate.now())
        set(value) {
            field = value
            invalidate()
        }
    var focusedDay: Int = LocalDate.now().dayOfWeek.value
        set(value) {
            val target = value.coerceIn(1, 7)
            if (field == target) return
            startFocusAnimation(field, target)
            field = target
        }

    // ◀ [추가] MainActivity로부터 공휴일 데이터를 전달받을 변수
    var holidays: List<HolidayItem> = emptyList()
        set(value) {
            field = value
            invalidate() // 데이터 세팅 시 화면 즉시 갱신
        }

    var onScheduleClick: ((ScheduleEntity) -> Unit)? = null
    var onDayFocus: ((LocalDate) -> Unit)? = null
    var onScheduleDrop: ((Long, LocalDate, Int, Int) -> Unit)? = null
    var onScheduleDragStarted: ((ScheduleEntity) -> Unit)? = null
    var headerOnly: Boolean = false
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    private val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val density = resources.displayMetrics.density
    private val headerHeight = 48f * density
    private val hourHeight = 58f * density
    private val startMinutes = 6 * 60
    private val endMinutes = 24 * 60
    private val corner = 7f * density
    private var dragPreview: DropPreview? = null
    private var previousFocusedDay = focusedDay
    private var focusAnimationProgress = 1f
    private var focusAnimator: ValueAnimator? = null
    private val gestureDetector = GestureDetectorCompat(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val schedule = scheduleAt(e.x, e.y)
                if (schedule != null) {
                    onScheduleClick?.invoke(schedule)
                    return true
                }
                dayAt(e.x)?.let { onDayFocus?.invoke(dateForDay(it)) }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                val schedule = scheduleAt(e.x, e.y) ?: return
                startScheduleDrag(schedule)
            }
        }
    )

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(24, 32, 42)
        textSize = 13f * density
    }
    private val smallTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(102, 112, 133)
        textSize = 11f * density
    }
    private val whiteTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 11f * density
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(216, 222, 232)
        strokeWidth = 1f
    }
    private val weekendBoundaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(116, 128, 145)
        strokeWidth = 1.5f * density
        pathEffect = DashPathEffect(floatArrayOf(6f * density, 5f * density), 0f)
    }
    private val previewStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        minimumHeight = (((endMinutes - startMinutes) / 60f) * hourHeight).roundToInt()
        setOnDragListener { _, event -> handleDrag(event) }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val fallbackWidth = (360f * density).roundToInt()
        val desiredHeight = if (headerOnly) headerHeight.roundToInt() else minimumHeight
        setMeasuredDimension(
            resolveSize(fallbackWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (headerOnly) {
            drawHeaders(canvas)
            return
        }
        drawGrid(canvas)
        drawPreview(canvas)
        drawSchedules(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (headerOnly) {
            if (event.action == MotionEvent.ACTION_UP) {
                dayAt(event.x)?.let { onDayFocus?.invoke(dateForDay(it)) }
            }
            return true
        }
        gestureDetector.onTouchEvent(event)
        return true
    }

    private fun drawHeaders(canvas: Canvas) {
        drawTextCentered(canvas, "", 0f, 0f, timeColumnWidth(), headerHeight, textPaint)
        for (day in 1..7) {
            val rect = headerColumnRect(day)
            val date = dateForDay(day)
            val isToday = date == LocalDate.now()
            val focusAmount = focusAmount(day)
            fillPaint.color = when {
                focusAmount > 0f -> ColorUtils.blendARGB(
                    if (isToday) Color.rgb(230, 244, 255) else Color.TRANSPARENT,
                    Color.rgb(34, 108, 224),
                    focusAmount
                )
                isToday -> Color.rgb(230, 244, 255)
                else -> Color.TRANSPARENT
            }
            if (fillPaint.color != Color.TRANSPARENT && Color.alpha(fillPaint.color) > 0) {
                canvas.drawRoundRect(rect, corner, corner, fillPaint)
            }
            val label = if (focusAmount > 0.5f) {
                "${dayLabels[day - 1]}\n${date.monthValue}/${date.dayOfMonth}"
            } else {
                "${dayLabels[day - 1].take(1)}\n${date.dayOfMonth}"
            }

            // ◀ [수정] 해당 요일이 공휴일인지 판별합니다.
            val isCurrentDateHoliday = checkIsHoliday(date)

            // ◀ [수정] 포커스 유무와 공휴일 여부를 조합하여 글자 색상을 바꿉니다.
            val normalColor = if (isCurrentDateHoliday) Color.rgb(214, 48, 49) else Color.rgb(24, 32, 42) // 공휴일이면 기본 빨간색

            val animatedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ColorUtils.blendARGB(normalColor, Color.WHITE, focusAmount) // 파란색 카드에 강조 선택되면 흰색글씨로 자동 보간
                textSize = 13f * density
                typeface = textPaint.typeface
            }
            drawMultilineCentered(canvas, label, rect, animatedTextPaint)
        }
    }

    // ◀ [추가] 날짜 매칭용 헬퍼 함수
    private fun checkIsHoliday(date: LocalDate): Boolean {
        val targetDateInt = date.year * 10000 + date.monthValue * 100 + date.dayOfMonth
        return holidays.any { it.locdate == targetDateInt }
    }

    private fun drawGrid(canvas: Canvas) {
        for (day in 1..7) {
            if (dateForDay(day) == LocalDate.now()) {
                val rect = columnRect(day, 0f, height.toFloat())
                fillPaint.color = Color.rgb(232, 244, 255)
                canvas.drawRect(rect, fillPaint)
            }
        }
        for (hour in 6..24) {
            val y = yForMinutes(hour * 60)
            canvas.drawLine(0f, y, width.toFloat(), y, linePaint)
            if (hour < 24) {
                canvas.drawText("%02d".format(hour), 5f * density, y + 15f * density, smallTextPaint)
            }
        }
        for (day in 1..7) {
            val left = xForDay(day)
            canvas.drawLine(left, 0f, left, height.toFloat(), linePaint)
        }
        canvas.drawLine(width.toFloat() - 1f, 0f, width.toFloat() - 1f, height.toFloat(), linePaint)
        drawWeekendBoundary(canvas, height.toFloat())
    }

    private fun drawWeekendBoundary(canvas: Canvas, bottom: Float) {
        val weekendStartX = xForDay(6)
        canvas.drawLine(weekendStartX, 0f, weekendStartX, bottom, weekendBoundaryPaint)
    }

    private fun drawPreview(canvas: Canvas) {
        val preview = dragPreview ?: return
        val schedule = schedules.firstOrNull { it.id == preview.scheduleId }
        val duration = schedule?.durationMinutes ?: 60
        val baseColor = schedule?.color ?: Color.rgb(34, 108, 224)
        val previewDays = if (schedule?.repeatType == RepeatType.DAILY) 1..7 else preview.day..preview.day
        for (day in previewDays) {
            val rect = columnRect(
                day,
                yForMinutes(preview.minutes),
                yForMinutes(preview.minutes + duration)
            ).apply {
                inset(3f * density, 2f * density)
            }
            fillPaint.color = ColorUtils.setAlphaComponent(baseColor, 92)
            previewStrokePaint.color = ColorUtils.setAlphaComponent(baseColor, 210)
            canvas.drawRoundRect(rect, corner, corner, fillPaint)
            canvas.drawRoundRect(rect, corner, corner, previewStrokePaint)
            if (day == focusedDay) {
                val label = "${schedule?.title ?: "Schedule"}  ${minutesToText(preview.minutes)}-${minutesToText(preview.minutes + duration)}"
                canvas.drawText(label, rect.left + 7f * density, rect.top + 17f * density, smallTextPaint)
            }
        }
    }

    private fun drawSchedules(canvas: Canvas) {
        for (schedule in schedules) {
            if (schedule.status == ScheduleStatus.INBOX) continue
            val start = schedule.startTimeMinutes ?: continue
            val duration = schedule.durationMinutes ?: 60
            for (day in 1..7) {
                val date = dateForDay(day)
                if (!occursOn(schedule, date)) continue
                val rect = columnRect(day, yForMinutes(start), yForMinutes(start + duration)).apply {
                    inset(3f * density, 2f * density)
                }
                fillPaint.color = if (schedule.status == ScheduleStatus.DONE) {
                    Color.rgb(145, 151, 163)
                } else {
                    schedule.color ?: Color.rgb(34, 108, 224)
                }
                canvas.drawRoundRect(rect, corner, corner, fillPaint)
                if (day == focusedDay) {
                    when {
                        duration <= 15 -> Unit
                        duration <= 30 -> canvas.drawText(
                            schedule.title,
                            rect.left + 7f * density,
                            rect.top + 15f * density,
                            whiteTextPaint
                        )
                        else -> {
                            val time = "${minutesToText(start)}-${minutesToText(start + duration)}"
                            canvas.drawText(schedule.title, rect.left + 7f * density, rect.top + 15f * density, whiteTextPaint)
                            canvas.drawText(time, rect.left + 7f * density, rect.top + 30f * density, whiteTextPaint)
                        }
                    }
                }
            }
        }
    }

    private fun scheduleAt(x: Float, y: Float): ScheduleEntity? {
        return schedules.asReversed().firstOrNull { schedule ->
            if (schedule.status == ScheduleStatus.INBOX) return@firstOrNull false
            val day = dayAt(x) ?: return@firstOrNull false
            val date = dateForDay(day)
            if (!occursOn(schedule, date)) return@firstOrNull false
            val start = schedule.startTimeMinutes ?: return@firstOrNull false
            val rect = columnRect(day, yForMinutes(start), yForMinutes(start + (schedule.durationMinutes ?: 60)))
            rect.contains(x, y)
        }
    }

    private fun occursOn(schedule: ScheduleEntity, date: LocalDate): Boolean {
        val anchor = schedule.scheduledDate?.let { LocalDate.ofEpochDay(it) } ?: return false
        return when (schedule.repeatType) {
            RepeatType.DAILY -> true
            RepeatType.WEEKLY -> date.dayOfWeek == anchor.dayOfWeek
            else -> date == anchor
        }
    }

    private fun startScheduleDrag(schedule: ScheduleEntity) {
        onScheduleDragStarted?.invoke(schedule)
        val data = ClipData.newPlainText("scheduleId", schedule.id.toString())
        startDragAndDrop(data, ScheduleDragShadowBuilder(schedule), schedule.id, 0)
    }

    private fun handleDrag(event: DragEvent): Boolean {
        return when (event.action) {
            DragEvent.ACTION_DRAG_LOCATION, DragEvent.ACTION_DRAG_ENTERED -> {
                val day = dayAt(event.x) ?: return true
                val minutes = minutesForY(event.y)
                val id = event.localState as? Long
                if (day != focusedDay) {
                    focusedDay = day
                    onDayFocus?.invoke(dateForDay(day))
                }
                dragPreview = DropPreview(day, minutes, id)
                invalidate()
                true
            }
            DragEvent.ACTION_DROP -> {
                val id = event.localState as? Long ?: return true
                val day = dayAt(event.x) ?: return true
                val minutes = minutesForY(event.y)
                dragPreview = null
                invalidate()
                onScheduleDrop?.invoke(id, dateForDay(day), day, minutes)
                true
            }
            DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> {
                dragPreview = null
                invalidate()
                true
            }
            else -> true
        }
    }

    private fun dayAt(x: Float): Int? {
        if (x < timeColumnWidth()) return null
        for (day in 1..7) {
            val left = xForDay(day)
            if (x >= left && x <= left + columnWidth(day)) return day
        }
        return null
    }

    private fun xForDay(day: Int): Float {
        var x = timeColumnWidth()
        for (d in 1 until day) x += columnWidth(d)
        return x
    }

    private fun timeColumnWidth(): Float = width / 12f * 2f

    private fun columnWidth(day: Int): Float = width / 12f * columnWeight(day)

    private fun columnWeight(day: Int): Float {
        if (focusAnimationProgress >= 1f || previousFocusedDay == focusedDay) {
            return if (day == focusedDay) 4f else 1f
        }
        return when (day) {
            previousFocusedDay -> 4f + (1f - 4f) * focusAnimationProgress
            focusedDay -> 1f + (4f - 1f) * focusAnimationProgress
            else -> 1f
        }
    }

    private fun focusAmount(day: Int): Float {
        if (focusAnimationProgress >= 1f || previousFocusedDay == focusedDay) {
            return if (day == focusedDay) 1f else 0f
        }
        return when (day) {
            previousFocusedDay -> 1f - focusAnimationProgress
            focusedDay -> focusAnimationProgress
            else -> 0f
        }
    }

    private fun startFocusAnimation(fromDay: Int, toDay: Int) {
        focusAnimator?.cancel()
        previousFocusedDay = fromDay
        focusAnimationProgress = 0f
        focusAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 180L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                focusAnimationProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun columnRect(day: Int, top: Float, bottom: Float): RectF {
        val left = xForDay(day)
        return RectF(left, top, left + columnWidth(day), bottom)
    }

    private fun yForMinutes(minutes: Int): Float {
        return ((minutes - startMinutes).coerceIn(0, endMinutes - startMinutes) / 60f) * hourHeight
    }

    private fun minutesForY(y: Float): Int {
        val raw = startMinutes + ((y.coerceAtLeast(0f) / hourHeight) * 60f).roundToInt()
        return ((raw / 15f).roundToInt() * 15).coerceIn(startMinutes, endMinutes - 15)
    }

    private fun headerColumnRect(day: Int): RectF {
        val left = xForDay(day)
        return RectF(left, 0f, left + columnWidth(day), headerHeight)
    }

    private fun dateForDay(day: Int): LocalDate = selectedWeekStart.plusDays((day - 1).toLong())

    private fun drawTextCentered(canvas: Canvas, text: String, left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
        val x = (left + right) / 2f - paint.measureText(text) / 2f
        val y = (top + bottom) / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, x, y, paint)
    }

    private fun drawMultilineCentered(canvas: Canvas, text: String, rect: RectF, paint: Paint) {
        val lines = text.split("\n")
        val lineHeight = 14f * density
        val startY = rect.centerY() - ((lines.size - 1) * lineHeight / 2f) - (paint.descent() + paint.ascent()) / 2f
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, rect.centerX() - paint.measureText(line) / 2f, startY + index * lineHeight, paint)
        }
    }

    private fun minutesToText(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    private data class DropPreview(val day: Int, val minutes: Int, val scheduleId: Long?)

    private inner class ScheduleDragShadowBuilder(
        private val schedule: ScheduleEntity
    ) : DragShadowBuilder(this) {
        private val shadowWidth = (width / 12f * 4f * 0.86f).roundToInt().coerceAtLeast((92f * density).roundToInt())
        private val shadowHeight = (((schedule.durationMinutes ?: 60) / 60f) * hourHeight)
            .roundToInt()
            .coerceAtLeast((42f * density).roundToInt())
            .coerceAtMost((150f * density).roundToInt())

        override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
            outShadowSize.set(shadowWidth, shadowHeight)
            outShadowTouchPoint.set(shadowWidth / 2, (18f * density).roundToInt())
        }

        override fun onDrawShadow(canvas: Canvas) {
            val rect = RectF(0f, 0f, shadowWidth.toFloat(), shadowHeight.toFloat())
            fillPaint.color = ColorUtils.setAlphaComponent(schedule.color ?: Color.rgb(34, 108, 224), 185)
            canvas.drawRoundRect(rect, corner, corner, fillPaint)
            val start = schedule.startTimeMinutes
            val subtitle = if (start != null) {
                "${minutesToText(start)}-${minutesToText(start + (schedule.durationMinutes ?: 60))}"
            } else {
                "${schedule.durationMinutes ?: 60} min"
            }
            canvas.drawText(schedule.title, 8f * density, 17f * density, whiteTextPaint)
            canvas.drawText(subtitle, 8f * density, 32f * density, whiteTextPaint)
        }
    }

    companion object {
        private fun weekStart(date: LocalDate): LocalDate {
            return date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        }
    }
}