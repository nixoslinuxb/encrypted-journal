package com.encryptedjournal.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.encryptedjournal.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                val db = AppDatabase.getDatabase(context)
                
                // Re-schedule general reminders
                val reminders = db.reminderDao().getAllReminders().first()
                val currentTime = System.currentTimeMillis()
                reminders.forEach { reminder ->
                    if (reminder.timestamp > currentTime) {
                        ReminderManager.scheduleReminderAlarm(context, reminder)
                    }
                }
                
                // Re-schedule story-specific reminders
                val stories = db.storyDao().getAllStories().first()
                stories.forEach { story ->
                    story.reminderTimestamp?.let { timestamp ->
                        if (timestamp > currentTime) {
                            ReminderManager.scheduleStoryAlarm(context, story)
                        }
                    }
                }
            }
        }
    }
}
