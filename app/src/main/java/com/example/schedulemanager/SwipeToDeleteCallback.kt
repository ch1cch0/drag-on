package com.example.schedulemanager

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.schedulemanager.ui.inbox.InboxScheduleAdapter

class SwipeToDeleteCallback(
    private val adapter: InboxScheduleAdapter,
    private val onDeleteSwiped: (position: Int) -> Unit
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

    private val backgroundPaint = Paint().apply { color = Color.parseColor("#D32F2F") }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 42f
        isAntiAlias = true
        isSubpixelText = true
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        onDeleteSwiped(viewHolder.adapterPosition)
    }

    override fun onChildDraw(

        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        val itemView = viewHolder.itemView

        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX < 0) {
            val background = RectF(
                itemView.right.toFloat() + dX,
                itemView.top.toFloat(),
                itemView.right.toFloat(),
                itemView.bottom.toFloat()
            )
            c.drawRect(background, backgroundPaint)

            val text = "삭제"
            val textWidth = textPaint.measureText(text)
            val itemHeight = itemView.bottom - itemView.top

            val textX = itemView.right.toFloat() - textWidth - 40f
            val textY = itemView.top.toFloat() + (itemHeight / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)

            if (-dX > textWidth + 60f) {
                c.drawText(text, textX, textY, textPaint)
            }
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }
}