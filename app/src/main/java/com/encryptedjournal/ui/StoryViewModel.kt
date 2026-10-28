package com.encryptedjournal.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.encryptedjournal.data.Story
import com.encryptedjournal.data.StoryDao
import com.encryptedjournal.data.Reminder
import com.encryptedjournal.data.ReminderDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.encryptedjournal.data.FileEncryptionManager
import java.io.File
import android.content.Context

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class StoryViewModel(
    private val storyDao: StoryDao,
    private val reminderDao: ReminderDao
) : ViewModel() {

    fun seedTurkishImportantDays(context: Context) {
        viewModelScope.launch {
            val currentYear = LocalDate.now().year
            val importantDays = listOf(
                Triple("New Year's Day", "Celebration marking the beginning of the new year.", 1 to 1),
                Triple("National Sovereignty and Children's Day", "Commemorates the foundation of the Grand National Assembly of Turkey, dedicated to children.", 4 to 23),
                Triple("Commemoration of Atatürk, Youth and Sports Day", "Commemorates Atatürk's arrival in Samsun in 1919, marking the start of the War of Independence.", 5 to 19),
                Triple("Democracy and National Unity Day", "Honors national unity and democracy in Turkey.", 7 to 15),
                Triple("Victory Day", "Commemorates the decisive Turkish victory in the Battle of Dumlupınar in 1922.", 8 to 30),
                Triple("Republic Day", "Commemorates the official proclamation of the Republic of Turkey in 1923.", 10 to 29),
                Triple("Commemoration of Atatürk (09:05)", "Commemorates Mustafa Kemal Atatürk, founder of the Republic of Turkey, who passed away in 1938.", 11 to 10)
            )

            val existingReminders = reminderDao.getAllRemindersOnce()
            
            for (year in listOf(currentYear, currentYear + 1)) {
                for ((title, note, date) in importantDays) {
                    val (month, day) = date
                    val targetDate = LocalDate.of(year, month, day)
                    val time = if (month == 11 && day == 10) LocalTime.of(9, 5) else LocalTime.of(9, 0)
                    val timestamp = targetDate.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                    val reminderTitle = "$title: $note"

                    // Check if already exists for this title and year
                    val alreadyExists = existingReminders.any { 
                        it.title.startsWith(title) && 
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(it.timestamp), ZoneId.systemDefault()).year == year 
                    }

                    if (!alreadyExists) {
                        val isAlarm = (month == 11 && day == 10)
                        val reminder = Reminder(
                            title = reminderTitle,
                            timestamp = timestamp,
                            isAlarm = isAlarm,
                            repeatType = "ONCE"
                        )
                        reminderDao.insertReminder(reminder)
                        if (timestamp > System.currentTimeMillis()) {
                            ReminderManager.scheduleReminderAlarm(context, reminder)
                        }
                    }
                }
            }
        }
    }
    val stories: StateFlow<List<Story>> = storyDao.getAllStories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    val reminders: StateFlow<List<Reminder>> = reminderDao.getAllReminders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    fun updateStory(context: Context, story: Story) {
        viewModelScope.launch {
            val encryptedImages = story.images.map { path -> encryptInternalFile(context, path) }
            val encryptedVideos = story.videos.map { path -> encryptInternalFile(context, path) }
            val encryptedVoices = story.voiceNotes.map { path -> encryptInternalFile(context, path) }

            val storyToSave = story.copy(
                images = encryptedImages,
                videos = encryptedVideos,
                voiceNotes = encryptedVoices
            )

            storyDao.updateStory(storyToSave)
            if (storyToSave.reminderTimestamp != null) {
                ReminderManager.scheduleStoryAlarm(context, storyToSave, isAlarm = false)
            }
            if (storyToSave.alarmTimestamp != null) {
                ReminderManager.scheduleStoryAlarm(context, storyToSave, isAlarm = true)
            }
        }
    }

    fun addReminder(context: Context, title: String, timestamp: Long, isAlarm: Boolean = false, repeatType: String = "ONCE", repeatDays: String = "") {
        viewModelScope.launch {
            val reminder = Reminder(title = title, timestamp = timestamp, isAlarm = isAlarm, repeatType = repeatType, repeatDays = repeatDays)
            reminderDao.insertReminder(reminder)
            ReminderManager.scheduleReminderAlarm(context, reminder)
        }
    }

    fun updateReminder(context: Context, reminder: Reminder) {
        viewModelScope.launch {
            reminderDao.insertReminder(reminder)
            ReminderManager.scheduleReminderAlarm(context, reminder)
        }
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            reminderDao.deleteReminder(reminder)
        }
    }

    fun setReminder(context: Context, story: Story, reminderTime: Long) {
        viewModelScope.launch {
            val updatedStory = story.copy(reminderTimestamp = reminderTime)
            storyDao.updateStory(updatedStory)
            ReminderManager.scheduleStoryAlarm(context, updatedStory, isAlarm = false)
        }
    }

    fun setAlarm(context: Context, story: Story, alarmTime: Long) {
        viewModelScope.launch {
            val updatedStory = story.copy(alarmTimestamp = alarmTime)
            storyDao.updateStory(updatedStory)
            ReminderManager.scheduleStoryAlarm(context, updatedStory, isAlarm = true)
        }
    }

    fun deleteStory(story: Story) {
        viewModelScope.launch {
            storyDao.deleteStory(story)
        }
    }

    fun addStory(context: Context, title: String, pages: List<String>, mood: String?, tags: List<String>, images: List<String>, videos: List<String>, voiceNotes: List<String>, timestamp: Long = System.currentTimeMillis(), fontSize: Int = 16, reminderTimestamp: Long? = null, alarmTimestamp: Long? = null, textColor: Int = -16777216, repeatType: String = "ONCE", repeatDays: String = "", location: String? = null, checklistJson: String = "", pageStylesJson: String = "") {
        viewModelScope.launch {
            val encryptedImages = images.map { path -> encryptInternalFile(context, path) }
            val encryptedVideos = videos.map { path -> encryptInternalFile(context, path) }
            val encryptedVoices = voiceNotes.map { path -> encryptInternalFile(context, path) }

            val newStory = Story(
                title = title,
                pages = pages,
                mood = mood,
                tags = tags,
                images = encryptedImages,
                videos = encryptedVideos,
                voiceNotes = encryptedVoices,
                timestamp = timestamp,
                fontSize = fontSize,
                reminderTimestamp = reminderTimestamp,
                alarmTimestamp = alarmTimestamp,
                textColor = textColor,
                repeatType = repeatType,
                repeatDays = repeatDays,
                location = location,
                checklistJson = checklistJson,
                pageStylesJson = pageStylesJson
            )
            storyDao.insertStory(newStory)
            if (reminderTimestamp != null) {
                ReminderManager.scheduleStoryAlarm(context, newStory, isAlarm = false)
            }
            if (alarmTimestamp != null) {
                ReminderManager.scheduleStoryAlarm(context, newStory, isAlarm = true)
            }
        }
    }

    private fun encryptInternalFile(context: Context, path: String): String {
        val file = File(path)
        if (!file.exists()) return path
        if (file.name.startsWith("enc_")) return path // already encrypted

        val encryptedFile = File(context.filesDir, "enc_${file.name}")
        FileEncryptionManager.encryptFile(context, file, encryptedFile)
        return encryptedFile.absolutePath
    }

    fun toggleStar(story: Story) {
        viewModelScope.launch {
            storyDao.updateStory(story.copy(isStarred = !story.isStarred))
        }
    }

    fun togglePin(story: Story) {
        viewModelScope.launch {
            storyDao.updateStory(story.copy(isPinned = !story.isPinned))
        }
    }
}
