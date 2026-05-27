package com.example.schedulemanager.data

import android.graphics.Color
import kotlinx.coroutines.flow.Flow

class ScheduleRepository(
    private val scheduleDao: ScheduleDao,
    private val categoryDao: CategoryDao
) {
    val schedules: Flow<List<ScheduleEntity>> = scheduleDao.observeSchedules()
    val categories: Flow<List<CategoryEntity>> = categoryDao.observeCategories()

    suspend fun seedDefaultsIfNeeded() {
        if (categoryDao.count() > 0) return
        categoryDao.insert(
            CategoryEntity(
                name = "Study",
                defaultColor = Color.rgb(34, 139, 230),
                defaultDurationMinutes = 60,
                defaultRepeatType = RepeatType.NONE
            )
        )
        categoryDao.insert(
            CategoryEntity(
                name = "Personal",
                defaultColor = Color.rgb(46, 160, 67),
                defaultDurationMinutes = 30,
                defaultRepeatType = RepeatType.NONE
            )
        )
    }

    suspend fun saveSchedule(schedule: ScheduleEntity): Long {
        return if (schedule.id == 0L) {
            scheduleDao.insert(schedule)
        } else {
            scheduleDao.update(schedule)
            schedule.id
        }
    }

    suspend fun deleteSchedule(schedule: ScheduleEntity) {
        scheduleDao.delete(schedule)
    }

    suspend fun saveCategory(category: CategoryEntity): Long {
        return if (category.id == 0L) {
            categoryDao.insert(category)
        } else {
            categoryDao.update(category)
            category.id
        }
    }

    suspend fun moveToInbox(schedule: ScheduleEntity) {
        scheduleDao.update(
            schedule.copy(
                status = ScheduleStatus.INBOX,
                scheduledDate = null,
                dayOfWeek = null,
                startTimeMinutes = null
            )
        )
    }

    suspend fun markDone(schedule: ScheduleEntity, done: Boolean) {
        scheduleDao.update(schedule.copy(status = if (done) ScheduleStatus.DONE else ScheduleStatus.SCHEDULED))
    }
}
