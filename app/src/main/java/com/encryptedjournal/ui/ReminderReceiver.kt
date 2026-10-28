package com.encryptedjournal.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.encryptedjournal.MainActivity
import com.encryptedjournal.R
import com.encryptedjournal.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    companion object {
        private var mediaPlayer: MediaPlayer? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        val storyId = intent.getStringExtra("storyId") ?: return
        val storyTitle = intent.getStringExtra("storyTitle") ?: "Journal Reminder"
        val isStory = intent.getBooleanExtra("isStory", false)
        val isAlarm = intent.getBooleanExtra("isAlarm", false)

        val action = intent.action
        if (action == "com.encryptedjournal.ACTION_STOP") {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(storyId.hashCode())
            return
        }

        showNotification(context, storyId, storyTitle, isStory, isAlarm)
        
        if (isAlarm) {
            playAlarmSound(context)
        }
    }

    private fun showNotification(context: Context, storyId: String, title: String, isStory: Boolean, isAlarm: Boolean) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "journal_reminders"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Journal Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders for your journal entries"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val activityIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("storyId", storyId)
            putExtra("isStory", isStory)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            storyId.hashCode(),
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Journal Reminder")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)

        if (isAlarm) {
            val stopIntent = Intent(context, ReminderReceiver::class.java).apply {
                action = "com.encryptedjournal.ACTION_STOP"
                putExtra("storyId", storyId)
            }
            val stopPendingIntent = PendingIntent.getBroadcast(
                context,
                storyId.hashCode() + 2,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_launcher_foreground, "Stop Alarm", stopPendingIntent)
            builder.setOngoing(true)
        }

        notificationManager.notify(storyId.hashCode(), builder.build())
    }

    private fun playAlarmSound(context: Context) {
        if (mediaPlayer != null) return

        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) 
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        mediaPlayer = MediaPlayer().apply {
            setDataSource(context, alarmUri)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            isLooping = true
            prepare()
            start()
        }
    }
}
