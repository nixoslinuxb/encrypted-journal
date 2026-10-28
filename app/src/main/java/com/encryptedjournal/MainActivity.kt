package com.encryptedjournal

import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import android.content.Intent
import android.util.Log
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.encryptedjournal.data.AppDatabase
import com.encryptedjournal.ui.AuthScreen
import com.encryptedjournal.ui.BiometricAuthManager
import com.encryptedjournal.ui.CalendarScreen
import com.encryptedjournal.ui.SplashScreen
import com.encryptedjournal.ui.StoryDetailScreen
import com.encryptedjournal.ui.StoryEditorScreen
import com.encryptedjournal.ui.StoryViewModel
import com.encryptedjournal.ui.TimelineScreen
import com.encryptedjournal.ui.theme.MyApplicationTheme

import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : FragmentActivity() {
    private val pendingStoryId = mutableStateOf<String?>(null)
    private val isStoryReminder = mutableStateOf(false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("storyId")?.let { id ->
            pendingStoryId.value = id
            isStoryReminder.value = intent.getBooleanExtra("isStory", false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            System.loadLibrary("sqlcipher")
        } catch (e: Exception) {
            Log.e("MainActivity", "Native library load failed", e)
        }
        
        enableEdgeToEdge()
        
        // Capture initial intent data
        intent.getStringExtra("storyId")?.let { id ->
            pendingStoryId.value = id
            isStoryReminder.value = intent.getBooleanExtra("isStory", false)
        }

        setContent {
            var isAuthenticated by remember { mutableStateOf(false) }
            val biometricAuthManager = remember { BiometricAuthManager(this) }

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { }

            LaunchedEffect(Unit) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
                biometricAuthManager.authenticate(
                    onSuccess = { isAuthenticated = true },
                    onError = { 
                        android.util.Log.e("MainActivity", "Biometric auth failed")
                    }
                )
            }

            MyApplicationTheme {
                Crossfade(targetState = isAuthenticated, label = "auth_transition") { authState ->
                    if (authState) {
                        var showSplash by remember { mutableStateOf(true) }

                        if (showSplash) {
                            SplashScreen(
                                onSplashFinished = { showSplash = false }
                            )
                        } else {
                            val navController = rememberNavController()
                        
                        val storyIdToNavigate = pendingStoryId.value
                        val isStory = isStoryReminder.value
                        LaunchedEffect(storyIdToNavigate, isStory) {
                            if (storyIdToNavigate != null) {
                                if (isStory) {
                                    navController.navigate("detail/$storyIdToNavigate")
                                } else {
                                    navController.navigate("editor")
                                }
                                pendingStoryId.value = null // Clear after navigation
                            }
                        }

                        val context = androidx.compose.ui.platform.LocalContext.current
                        val database = remember { AppDatabase.getDatabase(context) }
                        val storyViewModel: StoryViewModel = viewModel(
                            factory = object : ViewModelProvider.Factory {
                                @Suppress("UNCHECKED_CAST")
                                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                    return StoryViewModel(database.storyDao(), database.reminderDao()) as T
                                }
                            },
                        )

                        LaunchedEffect(Unit) {
                            storyViewModel.seedTurkishImportantDays(context)
                        }

                        Surface(modifier = Modifier.fillMaxSize()) {
                            NavHost(navController = navController, startDestination = "timeline") {
                                composable("timeline") {
                                    TimelineScreen(
                                        viewModel = storyViewModel,
                                        onAddStory = { date ->
                                            if (date != null) {
                                                navController.navigate("editor?date=${date.format(DateTimeFormatter.ISO_LOCAL_DATE)}")
                                            } else {
                                                navController.navigate("editor")
                                            }
                                        },
                                        onStoryClick = { story -> navController.navigate("detail/${story.id}") },
                                        onEditStory = { story -> navController.navigate("editor?storyId=${story.id}") }
                                    )
                                }
                                composable("calendar") {
                                    val stories by storyViewModel.stories.collectAsState()
                                    val reminders by storyViewModel.reminders.collectAsState()
                                    val context = androidx.compose.ui.platform.LocalContext.current
                                    
                                    CalendarScreen(
                                        stories = stories,
                                        reminders = reminders,
                                        onAddStoryForDate = { date -> 
                                            navController.navigate("editor?date=${date.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)}")
                                        },
                                        onAddReminder = { title, time, isAlarm, repeatType, repeatDays ->
                                            storyViewModel.addReminder(context, title, time, isAlarm, repeatType, repeatDays)
                                        },
                                        onUpdateReminder = { reminder ->
                                            storyViewModel.updateReminder(context, reminder)
                                        },
                                        onDeleteReminder = { reminder ->
                                            storyViewModel.deleteReminder(reminder)
                                        },
                                        onStoryClick = { story -> navController.navigate("detail/${story.id}") },
                                        onSeedHolidays = { storyViewModel.seedTurkishImportantDays(context) },
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                                composable("editor?storyId={storyId}&date={date}") { backStackEntry ->
                                    val storyId = backStackEntry.arguments?.getString("storyId")
                                    val dateStr = backStackEntry.arguments?.getString("date")
                                    val initialDate = dateStr?.let { LocalDate.parse(it) }

                                    val stories by storyViewModel.stories.collectAsState()
                                    val storyToEdit = if (storyId != null) {
                                        stories.find { it.id == storyId }
                                    } else null

                                    StoryEditorScreen(
                                        storyToEdit = storyToEdit,
                                        initialDate = initialDate,
                                        onSave = { title, pages, mood, tags, images, videos, voiceNotes, timestamp, fontSize, reminderTimestamp, alarmTimestamp, textColor, repeatType, repeatDays, location, checklistJson, pageStylesJson ->
                                            if (storyToEdit != null) {
                                                storyViewModel.updateStory(
                                                    context,
                                                    storyToEdit.copy(
                                                        title = title,
                                                        pages = pages,
                                                        mood = mood,
                                                        tags = tags,
                                                        images = images,
                                                        videos = videos,
                                                        voiceNotes = voiceNotes,
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
                                                )
                                            } else {
                                                storyViewModel.addStory(context, title, pages, mood, tags, images, videos, voiceNotes, timestamp, fontSize, reminderTimestamp, alarmTimestamp, textColor, repeatType, repeatDays, location, checklistJson, pageStylesJson)
                                            }
                                            navController.popBackStack()
                                        },
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                                composable("detail/{storyId}") { backStackEntry ->
                                    val storyId = backStackEntry.arguments?.getString("storyId")
                                    val stories by storyViewModel.stories.collectAsState()
                                    val story = stories.find { it.id == storyId }
                                    
                                    if (story != null) {
                                        StoryDetailScreen(
                                            story = story,
                                            onEdit = { navController.navigate("editor?storyId=${story.id}") },
                                            onBack = { navController.popBackStack() },
                                            onUpdateStory = { updated -> storyViewModel.updateStory(context, updated) }
                                        )
                                    } else {
                                        // Empty view instead of loading screen to avoid flash
                                        Surface(modifier = Modifier.fillMaxSize()) {}
                                    }
                                }
                            }
                        }
                    }
                } else {
                    AuthScreen(
                            onAuthenticate = {
                                biometricAuthManager.authenticate(
                                    onSuccess = { isAuthenticated = true },
                                    onError = { 
                                        android.util.Log.e("MainActivity", "Biometric auth failed")
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
