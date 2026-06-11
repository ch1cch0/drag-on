package com.example.schedulemanager.ui.schedule

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.schedulemanager.R
import com.example.schedulemanager.data.ScheduleEntity
import com.example.schedulemanager.data.ScheduleStatus
import com.google.android.material.button.MaterialButton
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ScheduleDetailDialog(
    private val context: Context,
    private val schedule: ScheduleEntity,
    private val categoryName: String,
    private val onDoneChanged: (Boolean) -> Unit,
    private val onMoveToInbox: () -> Unit,
    private val onDelete: () -> Unit,
    private val onEdit: () -> Unit
) {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun show() {
        val card = cardForm()
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        var detailDone = schedule.status == ScheduleStatus.DONE
        val titleText = TextView(context).apply {
            text = schedule.title
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(24, 32, 42))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val doneButton = iconButton(R.drawable.ic_check_24, detailDone)
        val deleteButton = iconButton(R.drawable.ic_trash_24)
        val inboxButton = iconButton(R.drawable.ic_inbox_24)
        header.addView(titleText)
        header.addView(doneButton)
        header.addView(deleteButton)
        header.addView(inboxButton)
        card.addView(header)
        card.addView(detailLine("Category", categoryName))
        card.addView(locationLine())
        card.addView(detailLine("Date", schedule.scheduledDate?.let { dateFromEpochDay(it).format(dateFormatter) } ?: "Inbox"))
        card.addView(detailLine("Time", schedule.startTimeMinutes?.let { "${minutesToText(it)} - ${minutesToText(it + durationOrDefault())}" } ?: "Unassigned"))
        card.addView(detailLine("Duration", schedule.durationMinutes?.let { "$it minutes" } ?: "Unset"))
        card.addView(detailLine("Repeat", schedule.repeatType?.name ?: "Unset"))
        card.addView(detailLine("Deadline", schedule.deadline?.let { dateFromEpochDay(it).format(dateFormatter) } ?: "None"))

        val dialog = AlertDialog.Builder(context)
            .setView(card)
            .setNegativeButton("Close", null)
            .setPositiveButton("Edit") { _, _ -> onEdit() }
            .create()
        doneButton.setOnClickListener {
            detailDone = !detailDone
            onDoneChanged(detailDone)
            styleDoneButton(doneButton, detailDone)
            card.alpha = if (detailDone) 0.72f else 1f
        }
        inboxButton.setOnClickListener {
            onMoveToInbox()
            dialog.dismiss()
        }
        deleteButton.setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle("Delete schedule?")
                .setMessage(schedule.title)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete") { _, _ ->
                    onDelete()
                    dialog.dismiss()
                }
                .show()
        }
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
        dialog.show()
    }

    private fun locationLine(): TextView = TextView(context).apply {
        val locationName = schedule.locationName
        text = "Location: ${locationName ?: "None"}"
        setPadding(0, dp(8), 0, 0)
        textSize = 14f
        if (locationName == null) {
            setTextColor(Color.rgb(102, 112, 133))
        } else {
            schedule.locationAddress?.let {
                text = "Location: $locationName\n$it"
            }
            setTextColor(Color.rgb(34, 108, 224))
            paint.isUnderlineText = true
            isClickable = true
            setOnClickListener { openMap() }
        }
    }

    private fun openMap() {
        val locationName = schedule.locationName ?: return
        val latitude = schedule.locationLatitude
        val longitude = schedule.locationLongitude
        val uri = if (latitude != null && longitude != null) {
            Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude(${Uri.encode(locationName)})")
        } else {
            Uri.parse("geo:0,0?q=${Uri.encode(locationName)}")
        }
        val intent = Intent(Intent.ACTION_VIEW, uri)
        if (intent.resolveActivity(context.packageManager) == null) {
            Toast.makeText(context, "No map app found.", Toast.LENGTH_SHORT).show()
            return
        }
        context.startActivity(intent)
    }

    private fun iconButton(iconRes: Int, selected: Boolean = false): MaterialButton {
        return MaterialButton(context).apply {
            text = ""
            icon = context.getDrawable(iconRes)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconPadding = 0
            insetTop = 0
            insetBottom = 0
            minWidth = dp(42)
            minimumWidth = dp(42)
            width = dp(42)
            height = dp(42)
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                marginStart = dp(6)
            }
            cornerRadius = dp(8)
            styleDoneButton(this, selected)
        }
    }

    private fun styleDoneButton(button: MaterialButton, selected: Boolean) {
        button.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (selected) Color.rgb(34, 108, 224) else Color.rgb(238, 241, 245)
        )
        button.iconTint = android.content.res.ColorStateList.valueOf(
            if (selected) Color.WHITE else Color.rgb(102, 112, 133)
        )
    }

    private fun detailLine(label: String, value: String): TextView = TextView(context).apply {
        text = "$label: $value"
        setPadding(0, dp(8), 0, 0)
        setTextColor(Color.rgb(102, 112, 133))
        textSize = 14f
    }

    private fun cardForm(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(12), dp(20), dp(12))
        background = android.graphics.drawable.GradientDrawable().apply {
            color = android.content.res.ColorStateList.valueOf(Color.WHITE)
            cornerRadius = dp(14).toFloat()
        }
    }

    private fun minutesToText(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    private fun dateFromEpochDay(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    private fun durationOrDefault(): Int = schedule.durationMinutes ?: 60

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
