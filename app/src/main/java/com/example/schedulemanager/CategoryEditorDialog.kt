package com.example.schedulemanager

import android.app.AlertDialog
import android.content.Context
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.Spinner
import com.example.schedulemanager.data.CategoryEntity
import com.example.schedulemanager.data.RepeatType

class CategoryEditorDialog(
    private val context: Context,
    private val category: CategoryEntity?,
    private val colorOptions: List<Pair<String, Int>>,
    private val onSave: (CategoryEntity) -> Unit
) {
    private val formViews = DialogFormViews(context)

    fun show() {
        val form = formViews.cardForm()
        val nameInput = EditText(context).apply {
            hint = "Name"
            setText(category?.name.orEmpty())
            setSingleLine(true)
        }
        val colorSpinner = Spinner(context).apply {
            adapter = formViews.simpleAdapter(listOf("Unset") + colorOptions.map { it.first })
            setSelection(colorOptions.indexOfFirst { it.second == category?.defaultColor }.takeIf { it >= 0 }?.plus(1) ?: 0)
        }
        val hourPicker = NumberPicker(context).apply {
            minValue = 0
            maxValue = 8
            value = (category?.defaultDurationMinutes ?: 0) / 60
            wrapSelectorWheel = false
        }
        val minuteValues = arrayOf("00", "15", "30", "45")
        val minutePicker = NumberPicker(context).apply {
            minValue = 0
            maxValue = minuteValues.lastIndex
            displayedValues = minuteValues
            value = ((category?.defaultDurationMinutes ?: 0) % 60) / 15
            wrapSelectorWheel = false
        }
        val repeatSpinner = Spinner(context).apply {
            adapter = formViews.simpleAdapter(listOf("Unset", "One-time", "Daily", "Weekly"))
            setSelection(
                when (category?.defaultRepeatType) {
                    null -> 0
                    RepeatType.NONE -> 1
                    RepeatType.DAILY -> 2
                    RepeatType.WEEKLY -> 3
                }
            )
        }

        form.addView(formViews.label("Name"))
        form.addView(nameInput)
        form.addView(formViews.label("Default color"))
        form.addView(colorSpinner)
        form.addView(formViews.label("Default duration"))
        form.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(hourPicker, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(minutePicker, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
        )
        form.addView(formViews.label("Default repeat"))
        form.addView(repeatSpinner)

        AlertDialog.Builder(context)
            .setTitle(if (category == null) "Add category" else "Edit category")
            .setView(form)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val name = nameInput.text.toString().trim()
                        if (name.isBlank()) {
                            nameInput.error = "Required"
                            return@setOnClickListener
                        }
                        onSave(
                            CategoryEntity(
                                id = category?.id ?: 0,
                                name = name,
                                defaultColor = colorSpinner.selectedItemPosition.takeIf { it > 0 }?.let { colorOptions[it - 1].second },
                                defaultDurationMinutes = ((hourPicker.value * 60) + formViews.minuteIndexToMinutes(minutePicker.value)).takeIf { it > 0 },
                                defaultRepeatType = when (repeatSpinner.selectedItemPosition) {
                                    1 -> RepeatType.NONE
                                    2 -> RepeatType.DAILY
                                    3 -> RepeatType.WEEKLY
                                    else -> null
                                }
                            )
                        )
                        dialog.dismiss()
                    }
                }
            }
            .show()
    }

}
