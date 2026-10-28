package com.encryptedjournal.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val timestamp: Long,
    val isAlarm: Boolean = false,
    val repeatType: String = "ONCE", // "ONCE" or "WEEKLY"
    val repeatDays: String = "" // Comma-separated day names or indices, e.g., "MONDAY,TUESDAY"
)
