package com.example.schedulemanager

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.schedulemanager.data.CategoryEntity

class CategoryManagerDialog(
    private val context: Context,
    private val categories: List<CategoryEntity>,
    private val onCategorySelected: (CategoryEntity) -> Unit,
    private val onAddCategory: () -> Unit
) {
    fun show() {
        val list = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = CategoryAdapter(categories, onCategorySelected)
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = cellBackground(Color.WHITE, Color.TRANSPARENT, 14)
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(360)))
        }
        AlertDialog.Builder(context)
            .setTitle("Categories")
            .setView(container)
            .setNegativeButton("Close", null)
            .setPositiveButton("+") { _, _ -> onAddCategory() }
            .show()
    }

    private fun cellBackground(fill: Int, stroke: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            color = android.content.res.ColorStateList.valueOf(fill)
            cornerRadius = dp(radiusDp).toFloat()
            if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
        }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private class CategoryAdapter(
        private val items: List<CategoryEntity>,
        private val onClick: (CategoryEntity) -> Unit
    ) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
            val textView = TextView(parent.context).apply {
                setPadding(18, 16, 18, 16)
                textSize = 16f
                setTypeface(typeface, Typeface.NORMAL)
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
}
