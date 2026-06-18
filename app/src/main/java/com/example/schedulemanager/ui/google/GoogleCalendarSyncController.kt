package com.example.schedulemanager.ui.google

import android.app.AlertDialog
import android.content.Intent
import android.content.Context
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleCoroutineScope
import com.example.schedulemanager.BuildConfig
import com.example.schedulemanager.data.ScheduleEntity
import com.example.schedulemanager.data.ScheduleRepository
import com.example.schedulemanager.data.ScheduleStatus
import com.example.schedulemanager.external.google.GoogleCalendarRepository
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.launch
import java.time.Instant

class GoogleCalendarSyncController(
    private val activity: AppCompatActivity,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val repository: ScheduleRepository,
    private val calendarRepository: GoogleCalendarRepository,
    private val authorizationLauncher: ActivityResultLauncher<IntentSenderRequest>
) {
    private val authorizationClient = Identity.getAuthorizationClient(activity)
    private val preferences = activity.getSharedPreferences("google_calendar_sync", Context.MODE_PRIVATE)
    private var cachedAccessToken: String? = null
    private var pendingAction: ((String) -> Unit)? = null
    private var syncEnabled = preferences.getBoolean(KEY_SYNC_ENABLED, false)

    fun showSyncDialog(schedules: List<ScheduleEntity>) {
        if (BuildConfig.GOOGLE_CALENDAR_CLIENT_ID.isBlank()) {
            Toast.makeText(activity, "Google Calendar client ID is missing.", Toast.LENGTH_SHORT).show()
            return
        }
        val scheduledCount = schedules.count { it.status != ScheduleStatus.INBOX && it.scheduledDate != null && it.startTimeMinutes != null }
        val message = if (syncEnabled) {
            "Google Calendar auto sync is on.\n\nScheduled items: $scheduledCount"
        } else {
            "Connect Google Calendar?\n\nScheduled items will be added to Google Calendar, and future app changes will sync automatically."
        }
        val builder = AlertDialog.Builder(activity)
            .setTitle("Google Calendar")
            .setMessage(message)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(if (syncEnabled) "Sync all" else "Start sync") { _, _ ->
                enableAndSyncAll(schedules)
            }
        if (syncEnabled) {
            builder.setNeutralButton("Turn off") { _, _ ->
                syncEnabled = false
                preferences.edit().putBoolean(KEY_SYNC_ENABLED, false).apply()
                Toast.makeText(activity, "Google Calendar sync off.", Toast.LENGTH_SHORT).show()
            }
        }
        builder.show()
    }

    fun syncAfterSave(schedule: ScheduleEntity) {
        if (!syncEnabled || !schedule.canSyncToGoogle()) return
        withAccessToken { token ->
            lifecycleScope.launch {
                syncSchedule(token, schedule)
            }
        }
    }

    fun syncAfterDelete(schedule: ScheduleEntity) {
        if (!syncEnabled) return
        val eventId = schedule.googleEventId ?: return
        withAccessToken { token ->
            lifecycleScope.launch {
                runCatching { calendarRepository.deleteEvent(token, eventId) }
                    .onFailure { Toast.makeText(activity, "Google Calendar delete failed.", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    fun onAuthorizationResult(result: ActivityResult) {
        val intent: Intent? = result.data
        val action = pendingAction
        pendingAction = null
        if (intent == null || action == null) {
            Toast.makeText(activity, "Google authorization canceled.", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching { authorizationClient.getAuthorizationResultFromIntent(intent) }
            .onSuccess { authorizationResult ->
                val token = authorizationResult.accessToken
                if (token.isNullOrBlank()) {
                    Toast.makeText(activity, "Google authorization failed.", Toast.LENGTH_SHORT).show()
                    return@onSuccess
                }
                cachedAccessToken = token
                action(token)
            }
            .onFailure {
                Toast.makeText(activity, "Google authorization failed.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun enableAndSyncAll(schedules: List<ScheduleEntity>) {
        syncEnabled = true
        preferences.edit().putBoolean(KEY_SYNC_ENABLED, true).apply()
        withAccessToken { token ->
            lifecycleScope.launch {
                val targets = schedules.filter { it.canSyncToGoogle() }
                var success = 0
                var failed = 0
                for (schedule in targets) {
                    val result = runCatching { syncSchedule(token, schedule) }
                    if (result.isSuccess) success++ else failed++
                }
                val message = if (failed == 0) {
                    "Google Calendar synced $success schedules."
                } else {
                    "Synced $success schedules. Failed $failed."
                }
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun syncSchedule(accessToken: String, schedule: ScheduleEntity) {
        val eventId = calendarRepository.upsertEvent(accessToken, schedule)
        repository.saveSchedule(
            schedule.copy(
                googleCalendarId = "primary",
                googleEventId = eventId,
                googleSyncedAt = Instant.now().toEpochMilli()
            )
        )
    }

    private fun withAccessToken(action: (String) -> Unit) {
        cachedAccessToken?.let {
            action(it)
            return
        }
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(CALENDAR_EVENTS_SCOPE)))
            .build()
        authorizationClient.authorize(request)
            .addOnSuccessListener { result -> handleAuthorizationResult(result, action) }
            .addOnFailureListener {
                Toast.makeText(activity, "Google authorization failed.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun handleAuthorizationResult(
        result: AuthorizationResult,
        action: (String) -> Unit
    ) {
        val token = result.accessToken
        if (!token.isNullOrBlank()) {
            cachedAccessToken = token
            action(token)
            return
        }
        if (result.hasResolution()) {
            pendingAction = action
            val pendingIntent = result.pendingIntent ?: return
            authorizationLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
            return
        }
        Toast.makeText(activity, "Google authorization failed.", Toast.LENGTH_SHORT).show()
    }

    private fun ScheduleEntity.canSyncToGoogle(): Boolean {
        return status != ScheduleStatus.INBOX && scheduledDate != null && startTimeMinutes != null
    }

    companion object {
        private const val CALENDAR_EVENTS_SCOPE = "https://www.googleapis.com/auth/calendar.events"
        private const val KEY_SYNC_ENABLED = "sync_enabled"
    }
}
