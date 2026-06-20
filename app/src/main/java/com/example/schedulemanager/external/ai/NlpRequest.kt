

package com.example.schedulemanager.external.ai

import com.google.gson.annotations.SerializedName


data class NlpResponse(
    @SerializedName("title")
    val title: String?,

    @SerializedName("scheduled_date")
    val scheduledDate: Long?,

    @SerializedName("start_time_minutes")
    val startTimeMinutes: Int?,

    @SerializedName("duration_minutes")
    val durationMinutes: Int?
)
