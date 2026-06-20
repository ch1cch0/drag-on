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

    private val backgroundPaint = Paint().apply { color = Color.parseColor("#D32F2F") } // 부드러운 빨간색
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

    // 💡 끝까지 스와이프하면 리스너가 실행되도록 구조 변경
    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        onDeleteSwiped(viewHolder.adapterPosition)
    }

    // 💡 스와이프되는 도중에 뒤쪽에 빨간색 배경과 '삭제' 글자를 동적으로 그려주는 로직
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
            // 1. 빨간색 배경 그리기
            val background = RectF(
                itemView.right.toFloat() + dX,
                itemView.top.toFloat(),
                itemView.right.toFloat(),
                itemView.bottom.toFloat()
            )
            c.drawRect(background, backgroundPaint)

            // 2. '삭제' 텍스트 배치 계산 및 그리기
            val text = "삭제"
            val textWidth = textPaint.measureText(text)
            val itemHeight = itemView.bottom - itemView.top

            // 글자가 카드가 밀리는 양에 따라 자연스럽게 따라오도록 설정
            val textX = itemView.right.toFloat() - textWidth - 40f
            val textY = itemView.top.toFloat() + (itemHeight / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)

            if (-dX > textWidth + 60f) {
                c.drawText(text, textX, textY, textPaint)
            }
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }
}