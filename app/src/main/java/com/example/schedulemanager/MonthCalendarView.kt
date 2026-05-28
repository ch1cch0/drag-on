package com.example.schedulemanager

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.floor
import kotlin.math.roundToInt

class MonthCalendarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    var displayedMonth: LocalDate = LocalDate.now().withDayOfMonth(1)
        set(value) {
            field = value.withDayOfMonth(1)
            invalidate()
        }
    var selectedDate: LocalDate = LocalDate.now()
        set(value) {
            field = value
            invalidate()
        }

    // ◀ [추가] MainActivity로부터 공휴일 데이터를 전달받을 변수
    var holidays: List<HolidayItem> = emptyList()
        set(value) {
            field = value
            invalidate() // 데이터가 들어오면 달력을 다시 그리도록 갱신
        }

    var onDateSelected: ((LocalDate) -> Unit)? = null
    var onMonthChanged: ((LocalDate) -> Unit)? = null
    var onTodaySelected: (() -> Unit)? = null
    var onMonthTitleSelected: ((LocalDate) -> Unit)? = null
    var showNavigation: Boolean = true
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val corner = 10f * density
    private val navHeight = 44f * density
    private val headerHeight = 34f * density
    private val cellGap = 5f * density
    private val monthTitleRect = RectF()
    private val previousRect = RectF()
    private val todayRect = RectF()
    private val nextRect = RectF()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = Color.rgb(216, 222, 232)
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(24, 32, 42)
        textAlign = Paint.Align.CENTER
        textSize = 18f * density
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(102, 112, 133)
        textAlign = Paint.Align.CENTER
        textSize = 12f * density
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 14f * density
    }
    private val navPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(34, 108, 224)
        textAlign = Paint.Align.CENTER
        textSize = 14f * density
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec).takeIf { it > 0 } ?: (360f * density).roundToInt()
        val cellWidth = (width - paddingStart - paddingEnd - cellGap * 6f) / 7f
        val cellHeight = cellWidth.coerceAtMost(54f * density)
        val desiredHeight = (
            paddingTop + navigationHeight() + headerHeight + cellHeight * 6f + cellGap * 5f + paddingBottom
            ).roundToInt()
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val contentLeft = paddingStart.toFloat()
        val contentRight = width - paddingEnd.toFloat()
        val contentWidth = contentRight - contentLeft
        val cellWidth = (contentWidth - cellGap * 6f) / 7f
        val cellHeight = cellWidth.coerceAtMost(54f * density)

        if (showNavigation) drawNavigation(canvas, contentLeft, contentRight)
        val headerTop = paddingTop + navigationHeight()
        drawDayHeader(canvas, contentLeft, headerTop, cellWidth)
        drawDates(canvas, contentLeft, headerTop + headerHeight, cellWidth, cellHeight)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        when {
            showNavigation && previousRect.contains(event.x, event.y) -> onMonthChanged?.invoke(displayedMonth.minusMonths(1))
            showNavigation && todayRect.contains(event.x, event.y) -> onTodaySelected?.invoke()
            showNavigation && nextRect.contains(event.x, event.y) -> onMonthChanged?.invoke(displayedMonth.plusMonths(1))
            showNavigation && monthTitleRect.contains(event.x, event.y) -> onMonthTitleSelected?.invoke(displayedMonth)
            else -> dateAt(event.x, event.y)?.let { onDateSelected?.invoke(it) }
        }
        return true
    }

    private fun drawNavigation(canvas: Canvas, left: Float, right: Float) {
        val top = paddingTop.toFloat()
        val buttonSize = 38f * density
        previousRect.set(left, top + 3f * density, left + buttonSize, top + 3f * density + buttonSize)
        nextRect.set(right - buttonSize, previousRect.top, right, previousRect.bottom)
        todayRect.set(nextRect.left - 76f * density, previousRect.top, nextRect.left - 8f * density, previousRect.bottom)
        monthTitleRect.set(previousRect.right + 8f * density, top, todayRect.left - 8f * density, top + navHeight)

        fillPaint.color = Color.WHITE
        listOf(previousRect, todayRect, nextRect).forEach {
            canvas.drawRoundRect(it, 8f * density, 8f * density, fillPaint)
            canvas.drawRoundRect(it, 8f * density, 8f * density, strokePaint)
        }
        drawCentered(canvas, "<", previousRect, navPaint)
        drawCentered(canvas, "Today", todayRect, navPaint)
        drawCentered(canvas, ">", nextRect, navPaint)
        drawCentered(canvas, "${displayedMonth.year}년 ${displayedMonth.monthValue}월", monthTitleRect, titlePaint)
    }

    private fun drawDayHeader(canvas: Canvas, left: Float, top: Float, cellWidth: Float) {
        for (index in 0 until 7) {
            headerPaint.color = weekendColor(index + 1) ?: Color.rgb(102, 112, 133)
            val x = left + index * (cellWidth + cellGap) + cellWidth / 2f
            canvas.drawText(dayLabels[index], x, top + 22f * density, headerPaint)
        }
    }

    private fun drawDates(canvas: Canvas, left: Float, top: Float, cellWidth: Float, cellHeight: Float) {
        val firstDay = displayedMonth.withDayOfMonth(1)
        val startOffset = firstDay.dayOfWeek.value - 1
        val daysInMonth = displayedMonth.lengthOfMonth()
        var dayNumber = 1 - startOffset
        repeat(6) { row ->
            repeat(7) { column ->
                val rect = cellRect(left, top, cellWidth, cellHeight, row, column)
                val date = if (dayNumber in 1..daysInMonth) displayedMonth.withDayOfMonth(dayNumber) else null
                drawDateCell(canvas, rect, date, column + 1)
                dayNumber++
            }
        }
    }

    private fun drawDateCell(canvas: Canvas, rect: RectF, date: LocalDate?, dayOfWeek: Int) {
        val today = LocalDate.now()
        val isSelected = date == selectedDate
        val isToday = date == today
        fillPaint.color = when {
            isSelected -> Color.rgb(34, 108, 224)
            isToday -> Color.rgb(230, 244, 255)
            date != null && isSameWeek(date, selectedDate) -> Color.rgb(246, 248, 251)
            else -> Color.WHITE
        }
        canvas.drawRoundRect(rect, corner, corner, fillPaint)
        strokePaint.color = if (isToday && !isSelected) Color.rgb(34, 108, 224) else Color.rgb(222, 227, 235)
        canvas.drawRoundRect(rect, corner, corner, strokePaint)

        // ◀ [수정] 공휴일 여부를 확인하여 텍스트 색상을 분기합니다.
        val isCurrentDateHoliday = date?.let { checkIsHoliday(it) } ?: false

        datePaint.color = when {
            date == null -> Color.TRANSPARENT
            isSelected -> Color.WHITE
            isCurrentDateHoliday -> Color.rgb(214, 48, 49) // 🔴 공휴일이면 강제로 빨간색 처리
            else -> weekendColor(dayOfWeek) ?: Color.rgb(24, 32, 42)
        }
        val baseline = rect.top + 23f * density
        canvas.drawText(date?.dayOfMonth?.toString().orEmpty(), rect.centerX(), baseline, datePaint)
    }

    // ◀ [추가] 날짜 매칭용 헬퍼 함수
    private fun checkIsHoliday(date: LocalDate): Boolean {
        val targetDateInt = date.year * 10000 + date.monthValue * 100 + date.dayOfMonth
        return holidays.any { it.locdate == targetDateInt }
    }

    private fun dateAt(x: Float, y: Float): LocalDate? {
        val contentLeft = paddingStart.toFloat()
        val contentWidth = width - paddingStart - paddingEnd.toFloat()
        val cellWidth = (contentWidth - cellGap * 6f) / 7f
        val cellHeight = cellWidth.coerceAtMost(54f * density)
        val top = paddingTop + navigationHeight() + headerHeight
        if (y < top) return null
        val column = floor((x - contentLeft) / (cellWidth + cellGap)).toInt()
        val row = floor((y - top) / (cellHeight + cellGap)).toInt()
        if (column !in 0..6 || row !in 0..5) return null
        val rect = cellRect(contentLeft, top, cellWidth, cellHeight, row, column)
        if (!rect.contains(x, y)) return null
        val firstDay = displayedMonth.withDayOfMonth(1)
        val dayNumber = row * 7 + column - (firstDay.dayOfWeek.value - 1) + 1
        return if (dayNumber in 1..displayedMonth.lengthOfMonth()) displayedMonth.withDayOfMonth(dayNumber) else null
    }

    private fun cellRect(left: Float, top: Float, cellWidth: Float, cellHeight: Float, row: Int, column: Int): RectF {
        val cellLeft = left + column * (cellWidth + cellGap)
        val cellTop = top + row * (cellHeight + cellGap)
        return RectF(cellLeft, cellTop, cellLeft + cellWidth, cellTop + cellHeight)
    }

    private fun drawCentered(canvas: Canvas, text: String, rect: RectF, paint: Paint) {
        val y = rect.centerY() - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, rect.centerX(), y, paint)
    }

    private fun navigationHeight(): Float = if (showNavigation) navHeight else 0f

    private fun weekendColor(dayOfWeek: Int): Int? = when (dayOfWeek) {
        6 -> Color.rgb(34, 108, 224)
        7 -> Color.rgb(214, 48, 49)
        else -> null
    }

    private fun isSameWeek(left: LocalDate, right: LocalDate): Boolean {
        return weekStart(left) == weekStart(right)
    }

    private fun weekStart(date: LocalDate): LocalDate {
        return date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    }
}
