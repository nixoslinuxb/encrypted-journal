package com.encryptedjournal.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.encryptedjournal.data.Reminder
import com.encryptedjournal.data.Story
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

object ReminderManager {

    fun scheduleReminderAlarm(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        if (!alarmManager.canScheduleExactAlarms()) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            return
        }

        // Calculate next occurrence if it's a repeating reminder and the time is in the past
        var targetTime = reminder.timestamp
        if (reminder.repeatType == "WEEKLY" && targetTime < System.currentTimeMillis()) {
            targetTime = calculateNextOccurrence(reminder)
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("storyId", reminder.id)
            putExtra("storyTitle", if (reminder.isAlarm) "Alarm: ${reminder.title}" else reminder.title)
            putExtra("isStory", false)
            putExtra("isAlarm", reminder.isAlarm)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (reminder.isAlarm) {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(targetTime, pendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                targetTime,
                pendingIntent
            )
        }
    }

    fun calculateNextOccurrence(repeatType: String, repeatDays: String, originalTimestamp: Long): Long {
        if (repeatType == "ONCE") return originalTimestamp

        val daysToRepeat = repeatDays.split(",").filter { it.isNotEmpty() }.map { DayOfWeek.valueOf(it) }
        if (daysToRepeat.isEmpty()) return originalTimestamp

        val now = LocalDateTime.now().plusSeconds(5)
        val originalTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(originalTimestamp), ZoneId.systemDefault())
        
        var candidate = now.withHour(originalTime.hour).withMinute(originalTime.minute).withSecond(0).withNano(0)
        
        if (candidate.isBefore(now)) {
            candidate = candidate.plusDays(1)
        }

        for (i in 0..7) {
            val checkDate = candidate.plusDays(i.toLong())
            if (daysToRepeat.contains(checkDate.dayOfWeek)) {
                return checkDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        }

        return originalTimestamp
    }

    fun calculateNextOccurrence(reminder: Reminder): Long {
        return calculateNextOccurrence(reminder.repeatType, reminder.repeatDays, reminder.timestamp)
    }

    fun scheduleStoryAlarm(context: Context, story: Story, isAlarm: Boolean = false) {
        var reminderTime = if (isAlarm) story.alarmTimestamp else story.reminderTimestamp
        if (reminderTime == null) return
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        if (!alarmManager.canScheduleExactAlarms()) {
            return
        }

        // Calculate next occurrence if it's a repeating story reminder and the time is in the past
        if (story.repeatType == "WEEKLY" && reminderTime < System.currentTimeMillis()) {
            reminderTime = calculateNextOccurrence(story.repeatType, story.repeatDays, reminderTime)
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("storyId", story.id)
            putExtra("storyTitle", if (isAlarm) "Alarm: ${story.title}" else story.title)
            putExtra("isStory", true)
            putExtra("isAlarm", isAlarm)
        }
        
        val requestCode = if (isAlarm) story.id.hashCode() + 1 else story.id.hashCode()
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (isAlarm) {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(reminderTime, pendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                reminderTime,
                pendingIntent
            )
        }
    }
}
