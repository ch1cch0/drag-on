package com.example.schedulemanager

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.schedulemanager.data.ScheduleEntity
import com.example.schedulemanager.data.ScheduleStatus
import com.example.schedulemanager.databinding.ActivityMainBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior

class InboxBottomSheetController(
    private val binding: ActivityMainBinding,
    private val adapter: InboxScheduleAdapter
) {
    private val context = binding.root.context
    private val behavior: BottomSheetBehavior<android.widget.LinearLayout> =
        BottomSheetBehavior.from(binding.inboxSheet)

    fun setup(onAddSchedule: () -> Unit, onManageCategories: () -> Unit) {
        binding.inboxRecycler.layoutManager = LinearLayoutManager(context)
        binding.inboxRecycler.adapter = adapter
        binding.addButton.setOnClickListener { onAddSchedule() }
        binding.categoryButton.setOnClickListener { onManageCategories() }
        installInsets()
    }

    fun collapse() {
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    fun setHidden(hidden: Boolean) {
        binding.inboxSheet.alpha = if (hidden) 0f else 1f
        binding.inboxSheet.isEnabled = !hidden
        binding.inboxSheet.isClickable = !hidden
        binding.inboxSheet.importantForAccessibility = if (hidden) {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
        behavior.isDraggable = !hidden
        if (!hidden) {
            binding.inboxSheet.post { collapse() }
        }
    }

    fun render(schedules: List<ScheduleEntity>) {
        val inboxItems = schedules.filter { it.status == ScheduleStatus.INBOX }
        binding.inboxTitle.text = "Inbox (${inboxItems.size})"
        adapter.submit(inboxItems)
    }

    fun animateIntoInbox() {
        binding.inboxRecycler.scaleX = 0.96f
        binding.inboxRecycler.scaleY = 0.96f
        binding.inboxRecycler.alpha = 0.55f
        binding.inboxRecycler.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(220).start()
    }

    private fun installInsets() {
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
                behavior.peekHeight = dp(92) + systemBars.bottom
                collapse()
            }
            insets
        }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
