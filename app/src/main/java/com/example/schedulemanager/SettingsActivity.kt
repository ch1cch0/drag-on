package com.example.schedulemanager

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.example.schedulemanager.data.AppDatabase
import com.example.schedulemanager.data.CategoryEntity
import com.example.schedulemanager.data.RepeatType
import com.example.schedulemanager.data.ScheduleEntity
import com.example.schedulemanager.data.ScheduleRepository
import com.example.schedulemanager.data.ScheduleStatus
import com.example.schedulemanager.databinding.ActivitySettingsBinding
import com.example.schedulemanager.external.google.GoogleAccountProfile
import com.example.schedulemanager.external.google.GoogleCalendarRepository
import com.example.schedulemanager.ui.google.GoogleCalendarSyncController
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var repository: ScheduleRepository
    private lateinit var googleAuthorizationLauncher: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var backupFileLauncher: ActivityResultLauncher<String>
    private lateinit var restoreFileLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var googleCalendarSyncController: GoogleCalendarSyncController

    private var schedules: List<ScheduleEntity> = emptyList()
    private var categories: List<CategoryEntity> = emptyList()
    private var pendingBackupJson: String? = null
    private var suppressSyncSwitchChange = false

    private val backupFileDateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        val database = AppDatabase.getInstance(this)
        repository = ScheduleRepository(database.scheduleDao(), database.categoryDao())

        googleAuthorizationLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            googleCalendarSyncController.onAuthorizationResult(result)
        }
        backupFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                writeBackupToUri(uri)
            } else {
                pendingBackupJson = null
            }
        }
        restoreFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { restoreBackupFromUri(it) }
        }

        googleCalendarSyncController = GoogleCalendarSyncController(
            activity = this,
            lifecycleScope = lifecycleScope,
            repository = repository,
            calendarRepository = GoogleCalendarRepository(),
            authorizationLauncher = googleAuthorizationLauncher
        )

        bindActions()
        observeData()
    }

    private fun bindActions() {
        binding.backButton.setOnClickListener { finish() }
        binding.googleAccountItem.setOnClickListener {
            googleCalendarSyncController.signIn { renderGoogleAccount(it ?: googleCalendarSyncController.cachedProfile()) }
        }
        binding.syncSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (suppressSyncSwitchChange) return@setOnCheckedChangeListener
            googleCalendarSyncController.setSyncEnabled(isChecked, schedules) {
                renderSyncState()
            }
        }
        binding.refreshSyncButton.setOnClickListener {
            googleCalendarSyncController.syncAll(schedules) { renderSyncState() }
        }
        binding.downloadBackupItem.setOnClickListener {
            startBackupDownload()
        }
        binding.restoreBackupItem.setOnClickListener {
            restoreFileLauncher.launch(arrayOf("application/json", "text/*"))
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            combine(repository.schedules, repository.categories) { scheduleList, categoryList ->
                scheduleList to categoryList
            }.collect { (scheduleList, categoryList) ->
                schedules = scheduleList
                categories = categoryList
                renderSyncState()
            }
        }
        renderGoogleAccount(googleCalendarSyncController.cachedProfile())
        renderSyncState()
    }

    private fun renderGoogleAccount(profile: GoogleAccountProfile?) {
        val accountLabel = profile?.email ?: profile?.name
        binding.accountNameText.text = accountLabel ?: "press 'Google account' to login"
        val pictureUrl = profile?.pictureUrl
        if (pictureUrl.isNullOrBlank()) {
            Glide.with(this)
                .load(R.drawable.ic_account_circle_96)
                .into(binding.profileImage)
        } else {
            Glide.with(this)
                .load(pictureUrl)
                .placeholder(R.drawable.ic_account_circle_96)
                .error(R.drawable.ic_account_circle_96)
                .transform(CircleCrop())
                .into(binding.profileImage)
        }
    }

    private fun renderSyncState() {
        val scheduledCount = schedules.count { it.status != ScheduleStatus.INBOX && it.scheduledDate != null && it.startTimeMinutes != null }
        val enabled = googleCalendarSyncController.isSyncEnabled()
        binding.syncSubtitleText.text = "scheduled items : $scheduledCount"
        binding.refreshSyncButton.visibility = if (enabled) android.view.View.VISIBLE else android.view.View.GONE
        suppressSyncSwitchChange = true
        binding.syncSwitch.isChecked = enabled
        suppressSyncSwitchChange = false
    }

    private fun startBackupDownload() {
        pendingBackupJson = buildBackupJson()
        val fileName = "schedule_backup_${LocalDate.now().format(backupFileDateFormatter)}.json"
        backupFileLauncher.launch(fileName)
    }

    private fun writeBackupToUri(uri: Uri) {
        val json = pendingBackupJson ?: return
        val result = runCatching {
            contentResolver.openOutputStream(uri)?.use { output ->
                output.write(json.toByteArray(Charsets.UTF_8))
            } ?: error("Could not open backup file.")
        }
        pendingBackupJson = null
        Toast.makeText(
            this,
            if (result.isSuccess) "Backup downloaded." else "Backup download failed.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun restoreBackupFromUri(uri: Uri) {
        lifecycleScope.launch {
            val result = runCatching {
                val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Could not open backup file.")
                val backup = JSONObject(text)
                val restoredCategories = backup.optJSONArray("categories").orEmpty()
                    .mapObjects { it.toCategoryEntity() }
                val restoredSchedules = backup.optJSONArray("schedules").orEmpty()
                    .mapObjects { it.toScheduleEntity() }

                restoredCategories.forEach { repository.importCategory(it) }
                restoredSchedules.forEach { repository.importSchedule(it) }
                restoredSchedules.size + restoredCategories.size
            }
            Toast.makeText(
                this@SettingsActivity,
                if (result.isSuccess) "Backup restored." else "Backup restore failed.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun buildBackupJson(): String {
        return JSONObject()
            .put("version", 1)
            .put("exportedAt", Instant.now().toString())
            .put("schedules", JSONArray().apply { schedules.forEach { put(it.toBackupJson()) } })
            .put("categories", JSONArray().apply { categories.forEach { put(it.toBackupJson()) } })
            .toString(2)
    }

    private fun ScheduleEntity.toBackupJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("title", title)
            .put("categoryId", jsonValue(categoryId))
            .put("color", jsonValue(color))
            .put("isRepeat", jsonValue(isRepeat))
            .put("repeatType", jsonValue(repeatType?.name))
            .put("durationMinutes", jsonValue(durationMinutes))
            .put("deadline", jsonValue(deadline))
            .put("locationName", jsonValue(locationName))
            .put("locationAddress", jsonValue(locationAddress))
            .put("locationLatitude", jsonValue(locationLatitude))
            .put("locationLongitude", jsonValue(locationLongitude))
            .put("googleCalendarId", jsonValue(googleCalendarId))
            .put("googleEventId", jsonValue(googleEventId))
            .put("googleSyncedAt", jsonValue(googleSyncedAt))
            .put("scheduledDate", jsonValue(scheduledDate))
            .put("dayOfWeek", jsonValue(dayOfWeek))
            .put("startTimeMinutes", jsonValue(startTimeMinutes))
            .put("status", status.name)
    }

    private fun CategoryEntity.toBackupJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("name", name)
            .put("defaultColor", jsonValue(defaultColor))
            .put("defaultDurationMinutes", jsonValue(defaultDurationMinutes))
            .put("defaultRepeatType", jsonValue(defaultRepeatType?.name))
    }

    private fun JSONObject.toScheduleEntity(): ScheduleEntity {
        return ScheduleEntity(
            id = optLong("id", 0L),
            title = optString("title", "Untitled"),
            categoryId = nullableLong("categoryId"),
            color = nullableInt("color"),
            isRepeat = nullableBoolean("isRepeat"),
            repeatType = nullableString("repeatType")?.let { runCatching { RepeatType.valueOf(it) }.getOrNull() },
            durationMinutes = nullableInt("durationMinutes"),
            deadline = nullableLong("deadline"),
            locationName = nullableString("locationName"),
            locationAddress = nullableString("locationAddress"),
            locationLatitude = nullableDouble("locationLatitude"),
            locationLongitude = nullableDouble("locationLongitude"),
            googleCalendarId = nullableString("googleCalendarId"),
            googleEventId = nullableString("googleEventId"),
            googleSyncedAt = nullableLong("googleSyncedAt"),
            scheduledDate = nullableLong("scheduledDate"),
            dayOfWeek = nullableInt("dayOfWeek"),
            startTimeMinutes = nullableInt("startTimeMinutes"),
            status = nullableString("status")
                ?.let { runCatching { ScheduleStatus.valueOf(it) }.getOrNull() }
                ?: ScheduleStatus.INBOX
        )
    }

    private fun JSONObject.toCategoryEntity(): CategoryEntity {
        return CategoryEntity(
            id = optLong("id", 0L),
            name = optString("name", "Category"),
            defaultColor = nullableInt("defaultColor"),
            defaultDurationMinutes = nullableInt("defaultDurationMinutes"),
            defaultRepeatType = nullableString("defaultRepeatType")
                ?.let { runCatching { RepeatType.valueOf(it) }.getOrNull() }
        )
    }

    private fun JSONArray?.orEmpty(): JSONArray = this ?: JSONArray()

    private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> {
        return buildList {
            for (index in 0 until length()) {
                optJSONObject(index)?.let { add(transform(it)) }
            }
        }
    }

    private fun JSONObject.nullableString(name: String): String? {
        return if (isNull(name)) null else optString(name)
    }

    private fun JSONObject.nullableInt(name: String): Int? {
        return if (isNull(name)) null else optInt(name)
    }

    private fun JSONObject.nullableLong(name: String): Long? {
        return if (isNull(name)) null else optLong(name)
    }

    private fun JSONObject.nullableDouble(name: String): Double? {
        return if (isNull(name)) null else optDouble(name)
    }

    private fun JSONObject.nullableBoolean(name: String): Boolean? {
        return if (isNull(name)) null else optBoolean(name)
    }

    private fun jsonValue(value: Any?): Any = value ?: JSONObject.NULL
}
