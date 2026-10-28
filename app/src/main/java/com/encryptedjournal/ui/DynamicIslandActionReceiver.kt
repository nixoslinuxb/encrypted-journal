package com.encryptedjournal.ui

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.encryptedjournal.data.AppDatabase
import com.encryptedjournal.data.Reminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class DynamicIslandActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val notificationId = intent.getIntExtra("notificationId", 0)
        val storyId = intent.getStringExtra("storyId") ?: ""
        val storyTitle = intent.getStringExtra("storyTitle") ?: "Reminder"
        val isStory = intent.getBooleanExtra("isStory", false)
        val isAlarm = intent.getBooleanExtra("isAlarm", false)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)

        if (action == "com.encryptedjournal.ACTION_REPEAT") {
            // Repeat after 5 minutes
            val snoozeTime = System.currentTimeMillis() + 5 * 60 * 1000
            
            if (isStory) {
                // For stories, we just schedule a one-time snooze alarm
                val database = AppDatabase.getDatabase(context)
                CoroutineScope(Dispatchers.IO).launch {
                    val story = database.storyDao().getStoryById(storyId)
                    if (story != null) {
                        // Create a temporary reminder for the snooze
                        val snoozedReminder = Reminder(
                            title = "Snooze: ${story.title}",
                            timestamp = snoozeTime,
                            isAlarm = isAlarm
                        )
                        ReminderManager.scheduleReminderAlarm(context, snoozedReminder)
                    }
                }
            } else {
                val snoozedReminder = Reminder(
                    id = UUID.randomUUID().toString(),
                    title = storyTitle,
                    timestamp = snoozeTime,
                    isAlarm = isAlarm
                )
                ReminderManager.scheduleReminderAlarm(context, snoozedReminder)
            }
        }
    }
}
