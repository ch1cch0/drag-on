package com.example.schedulemanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val defaultColor: Int?,
    val defaultDurationMinutes: Int?,
    val defaultRepeatType: RepeatType?
)
