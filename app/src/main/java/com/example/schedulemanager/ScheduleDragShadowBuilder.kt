package com.example.schedulemanager

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.view.View
import androidx.core.graphics.ColorUtils
import com.example.schedulemanager.data.ScheduleEntity
import kotlin.math.roundToInt

class ScheduleDragShadowBuilder(
    owner: View,
    private val schedule: ScheduleEntity,
    private val density: Float,
    private val hourHeight: Float
) : View.DragShadowBuilder(owner) {
    private val corner = 7f * density
    private val shadowWidth = (owner.width / 12f * 4f * 0.86f).roundToInt().coerceAtLeast((92f * density).roundToInt())
    private val shadowHeight = (((schedule.durationMinutes ?: 60) / 60f) * hourHeight)
        .roundToInt()
        .coerceAtLeast((42f * density).roundToInt())
        .coerceAtMost((150f * density).roundToInt())
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val whiteTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 11f * density
    }

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
            "${ScheduleOccurrence.minutesToText(start)}-${ScheduleOccurrence.minutesToText(start + (schedule.durationMinutes ?: 60))}"
        } else {
            "${schedule.durationMinutes ?: 60} min"
        }
        canvas.drawText(schedule.title, 8f * density, 17f * density, whiteTextPaint)
        canvas.drawText(subtitle, 8f * density, 32f * density, whiteTextPaint)
    }
}
