package com.example.schedulemanager

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class DialogFormViews(
    private val context: Context
) {
    fun simpleAdapter(values: List<String>): ArrayAdapter<String> {
        return ArrayAdapter(context, android.R.layout.simple_spinner_item, values).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    fun cardForm(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(12), dp(20), dp(12))
        background = cellBackground(Color.WHITE, Color.TRANSPARENT, 14)
    }

    fun label(text: String): TextView = TextView(context).apply {
        this.text = text
        setPadding(0, dp(12), 0, dp(2))
        setTextColor(Color.rgb(102, 112, 133))
        textSize = 12f
    }

    fun scrollWrap(content: View): ScrollView = ScrollView(context).apply {
        addView(content)
    }

    fun cellBackground(fill: Int, stroke: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            color = android.content.res.ColorStateList.valueOf(fill)
            cornerRadius = dp(radiusDp).toFloat()
            if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
        }
    }

    fun minuteIndexToMinutes(index: Int): Int {
        return when (index) {
            1 -> 15
            2 -> 30
            3 -> 45
            else -> 0
        }
    }

    fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
