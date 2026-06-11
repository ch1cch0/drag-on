package com.example.schedulemanager

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.schedulemanager.data.ScheduleEntity
import com.example.schedulemanager.databinding.ItemScheduleBinding

class InboxScheduleAdapter(
    private val onClick: (ScheduleEntity) -> Unit,
    private val onLongClick: (ScheduleEntity, View) -> Boolean,
    private val categoryName: (Long?) -> String
) : RecyclerView.Adapter<InboxScheduleAdapter.ScheduleViewHolder>() {
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

    class ScheduleViewHolder(
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
