package com.example.schedulemanager.ui.location

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.schedulemanager.external.KakaoPlace

class LocationSearchDialog(
    private val context: Context,
    private val places: List<KakaoPlace>,
    private val onSelected: (KakaoPlace) -> Unit
) {
    private val density = context.resources.displayMetrics.density

    fun show() {
        lateinit var dialog: AlertDialog
        val list = resultList { place ->
            onSelected(place)
            dialog.dismiss()
        }
        dialog = AlertDialog.Builder(context)
            .setTitle("Select location")
            .setView(list)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            list.layoutParams = list.layoutParams.apply {
                height = dp(260)
            }
            list.requestLayout()
        }
        dialog.show()
    }

    private fun resultList(onClick: (KakaoPlace) -> Unit): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = dp(260)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260))
            addView(
                RecyclerView(context).apply {
                    layoutManager = LinearLayoutManager(context)
                    adapter = LocationAdapter(places, onClick)
                    overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                    isNestedScrollingEnabled = true
                    setPadding(0, dp(4), 0, dp(4))
                    addItemDecoration(LocationDividerDecoration())
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260))
            )
        }
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    private class LocationDividerDecoration : RecyclerView.ItemDecoration() {
        override fun onDrawOver(canvas: android.graphics.Canvas, parent: RecyclerView, state: RecyclerView.State) {
            val paint = android.graphics.Paint().apply { color = Color.rgb(226, 231, 238) }
            for (index in 0 until parent.childCount - 1) {
                val child = parent.getChildAt(index)
                val y = child.bottom.toFloat()
                canvas.drawRect(
                    parent.paddingLeft.toFloat(),
                    y,
                    (parent.width - parent.paddingRight).toFloat(),
                    y + 1,
                    paint
                )
            }
        }
    }

    private class LocationAdapter(
        private val items: List<KakaoPlace>,
        private val onClick: (KakaoPlace) -> Unit
    ) : RecyclerView.Adapter<LocationAdapter.LocationViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocationViewHolder {
            val density = parent.resources.displayMetrics.density
            val container = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((18 * density).toInt(), (12 * density).toInt(), (18 * density).toInt(), (12 * density).toInt())
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            val nameText = TextView(parent.context).apply {
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.rgb(24, 32, 42))
            }
            val addressText = TextView(parent.context).apply {
                textSize = 12f
                setTextColor(Color.rgb(112, 124, 140))
                setPadding(0, (3 * density).toInt(), 0, 0)
            }
            container.addView(nameText)
            container.addView(addressText)
            return LocationViewHolder(container, nameText, addressText, onClick)
        }

        override fun onBindViewHolder(holder: LocationViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        private class LocationViewHolder(
            itemView: View,
            private val nameText: TextView,
            private val addressText: TextView,
            private val onClick: (KakaoPlace) -> Unit
        ) : RecyclerView.ViewHolder(itemView) {
            fun bind(place: KakaoPlace) {
                nameText.text = place.name
                addressText.text = place.metaText
                itemView.setOnClickListener { onClick(place) }
            }
        }
    }
}
