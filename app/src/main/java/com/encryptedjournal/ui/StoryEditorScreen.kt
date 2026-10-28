package com.encryptedjournal.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.view.SoundEffectConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.encryptedjournal.R
import com.encryptedjournal.data.Story
import com.encryptedjournal.data.PageStyle
import com.encryptedjournal.data.FileEncryptionManager
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.ZoneId
import android.util.Log

import android.media.MediaMetadataRetriever
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import kotlin.math.absoluteValue
import java.time.LocalTime
import java.time.Instant
import java.time.LocalDateTime
import java.time.DayOfWeek
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextDecoration
import com.encryptedjournal.data.ChecklistItem
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SecureEditorThumbnail(path: String, isVideo: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    LaunchedEffect(path) {
        val file = File(path)
        if (!file.exists()) return@LaunchedEffect
        
        val decryptedFile = if (file.name.startsWith("enc_")) {
            withContext(Dispatchers.IO) { FileEncryptionManager.decryptToTempFile(context, file) }
        } else {
            file
        }
        
        if (decryptedFile != null) {
            val loadedBitmap = if (isVideo) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(decryptedFile.absolutePath)
                    retriever.getFrameAtTime(1000000)
                } catch (_: Exception) {
                    null
                } finally {
                    retriever.release()
                }
            } else {
                BitmapFactory.decodeFile(decryptedFile.absolutePath)
            }
            bitmap = loadedBitmap
            if (decryptedFile != file) { decryptedFile.delete() }
        }
    }

    Box(modifier = Modifier.size(100.dp).clip(RoundedCornerShape(8.dp)).background(Color.Gray.copy(alpha = 0.1f)).clickable { onClick() }, contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (isVideo) { Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(24.dp)) }
        } else {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
fun ModernAudioWaveform(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 16,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
) {
    val transition = rememberInfiniteTransition(label = "waveform_anim")
    val heights = List(barCount) { i ->
        if (isPlaying) {
            transition.animateFloat(
                initialValue = 0.15f + (i % 4) * 0.1f,
                targetValue = 0.95f - (i % 3) * 0.1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 280 + (i * 65) % 350,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$i"
            ).value
        } else {
            0.25f + (i % 5) * 0.12f
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        heights.forEach { fraction ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(fraction.coerceIn(0.15f, 1f))
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isPlaying) activeColor else inactiveColor)
            )
        }
    }
}

@Composable
fun ModernVoiceNoteCard(
    index: Int,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onPlayToggle,
        shape = RoundedCornerShape(28.dp),
        color = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            width = if (isPlaying) 1.5.dp else 1.dp,
            color = if (isPlaying) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        shadowElevation = if (isPlaying) 4.dp else 2.dp,
        modifier = modifier.width(180.dp).height(56.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = CircleShape,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = if (isPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            ModernAudioWaveform(
                isPlaying = isPlaying,
                barCount = 18,
                activeColor = MaterialTheme.colorScheme.primary,
                inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .padding(horizontal = 6.dp)
            )

            if (onDelete != null) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete Voice",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AudioWaveformBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 12,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
) {
    ModernAudioWaveform(
        isPlaying = isPlaying,
        modifier = modifier,
        barCount = barCount,
        activeColor = activeColor,
        inactiveColor = inactiveColor
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryEditorScreen(
    storyToEdit: Story? = null,
    initialDate: LocalDate? = null,
    onSave: (title: String, pages: List<String>, mood: String?, tags: List<String>, images: List<String>, videos: List<String>, voiceNotes: List<String>, timestamp: Long, fontSize: Int, reminderTimestamp: Long?, alarmTimestamp: Long?, textColor: Int, repeatType: String, repeatDays: String, location: String?, checklistJson: String, pageStylesJson: String) -> Unit,
    onBack: () -> Unit,
) {
    var title by remember { mutableStateOf(storyToEdit?.title ?: "") }
    var location by remember { mutableStateOf<String?>(storyToEdit?.location) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var showInAppMapDialog by remember { mutableStateOf(false) }
    var tempLocationInput by remember { mutableStateOf("") }
    var fontSize by remember { mutableIntStateOf(storyToEdit?.fontSize ?: 16) }
    var textColor by remember { mutableIntStateOf(storyToEdit?.textColor ?: -16777216) }
    val pageStylesMap = remember {
        mutableStateMapOf<Int, PageStyle>().apply {
            if (storyToEdit != null) {
                putAll(storyToEdit.pageStyles)
            }
        }
    }
    val pages = remember { 
        mutableStateListOf<TextFieldValue>().apply {
            if (storyToEdit != null) {
                addAll(storyToEdit.pages.map { TextFieldValue(it) })
            } else {
                add(TextFieldValue(""))
            }
        }
    }
    var showSizeMenuForPage by remember { mutableStateOf<Int?>(null) }
    var selectedMood by remember { mutableStateOf<String?>(storyToEdit?.mood) }
    var tagsInput by remember { mutableStateOf(storyToEdit?.tags?.joinToString(", ") ?: "") }
    val images = remember { 
        mutableStateListOf<String>().apply {
            if (storyToEdit != null) {
                addAll(storyToEdit.images)
            }
        }
    }
    val videos = remember {
        mutableStateListOf<String>().apply {
            if (storyToEdit != null) {
                addAll(storyToEdit.videos)
            }
        }
    }
    val voiceNotes = remember { 
        mutableStateListOf<String>().apply {
            if (storyToEdit != null) {
                addAll(storyToEdit.voiceNotes)
            }
        }
    }
    
    var currentlyPlayingVoice by remember { mutableStateOf<String?>(null) }
    var focusedPageIndex by remember { mutableIntStateOf(-1) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var reminderTimestamp by remember { mutableStateOf(storyToEdit?.reminderTimestamp) }
    var alarmTimestamp by remember { mutableStateOf(storyToEdit?.alarmTimestamp) }
    var repeatType by remember { mutableStateOf(storyToEdit?.repeatType ?: "ONCE") }
    var selectedDays by remember { mutableStateOf(storyToEdit?.repeatDays?.split(",")?.filter { it.isNotEmpty() }?.map { DayOfWeek.valueOf(it) }?.toSet() ?: setOf<DayOfWeek>()) }
    var showFullScreenPages by remember { mutableStateOf(false) }

    val pageChecklists = remember {
        mutableStateMapOf<Int, List<ChecklistItem>>().apply {
            if (storyToEdit != null) {
                putAll(storyToEdit.pageChecklists)
            }
        }
    }
    val pageChecklistVisibilities = remember { mutableStateMapOf<Int, Boolean>() }
    var newChecklistItemText by remember { mutableStateOf("") }

    var showEmojiPickerModal by remember { mutableStateOf(false) }
    var showTextEmojiPickerModal by remember { mutableStateOf(false) }
    var customSpInput by remember { mutableStateOf(fontSize.toString()) }
    var showCustomColorDialog by remember { mutableStateOf(false) }
    val savedSelections = remember { mutableStateMapOf<Int, TextRange>() }

    val pagerState = rememberPagerState(pageCount = { pages.size })

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current

    val timestamp = remember {
        if (storyToEdit != null) {
            storyToEdit.timestamp
        } else if (initialDate != null) {
            initialDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } else {
            System.currentTimeMillis()
        }
    }
    
    val context = LocalContext.current
    val voiceRecorder = remember { VoiceRecorder(context) }
    val voicePlayer = remember { VoicePlayer() }
    var isRecording by remember { mutableStateOf(false) }

    var recordingDurationSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingDurationSeconds = 0
            while (isRecording) {
                delay(1000)
                recordingDurationSeconds++
            }
        } else {
            recordingDurationSeconds = 0
        }
    }

    val formattedTimer = remember(recordingDurationSeconds) {
        val mins = recordingDurationSeconds / 60
        val secs = recordingDurationSeconds % 60
        String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
    }

    var showCamera by remember { mutableStateOf(false) }
    var fullScreenMediaIndex by remember { mutableStateOf<Int?>(null) }
    val allMedia = remember(images, videos) { images.map { it to false } + videos.map { it to true } }
    
    val pageTurnPlayer = remember {
        MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .build()
            )
        }
    }

    fun playPageTurn() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        view.playSoundEffect(SoundEffectConstants.CLICK)
        
        scope.launch(Dispatchers.IO) {
            try {
                pageTurnPlayer.reset()
                val afd = context.resources.openRawResourceFd(R.raw.page_flip)
                pageTurnPlayer.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                pageTurnPlayer.prepare()
                pageTurnPlayer.start()
            } catch (e: Exception) {
                Log.e("StoryEditor", "Error playing high-quality sound", e)
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (cameraGranted) {
            showCamera = true
        }
    }

    val voicePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted
        }
    }

    var isFetchingLocation by remember { mutableStateOf(false) }
    var isRealTimeTracking by remember { mutableStateOf(false) }

    DisposableEffect(isRealTimeTracking) {
        if (isRealTimeTracking) {
            val stopTracking = LocationUtils.startRealTimeLocationUpdates(
                context = context,
                scope = scope,
                onLocationFetched = { placeName, _, _ ->
                    isFetchingLocation = false
                    location = placeName
                    tempLocationInput = placeName
                },
                onError = { err ->
                    isFetchingLocation = false
                    isRealTimeTracking = false
                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                }
            )
            onDispose {
                stopTracking()
            }
        } else {
            onDispose {}
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            isFetchingLocation = true
            isRealTimeTracking = true
        } else {
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    fun fetchCurrentLocation() {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            isFetchingLocation = true
            isRealTimeTracking = true
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }



    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { uri ->
            val inputStream = context.contentResolver.openInputStream(uri)
            val file = File(context.filesDir, "img_${System.currentTimeMillis()}_${uri.lastPathSegment}")
            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            images.add(file.absolutePath)
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { uri ->
            val inputStream = context.contentResolver.openInputStream(uri)
            val file = File(context.filesDir, "vid_${System.currentTimeMillis()}_${uri.lastPathSegment}")
            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            videos.add(file.absolutePath)
        }
    }

    val moods = listOf("😊", "😔", "😠", "😴", "🤩", "🤔", "🥳", "🌈", "☕", "🐱", "😭", "😤", "🥰", "😎", "🥺", "🥶", "🫠", "🤯", "🤢", "🤡", "🍕", "🎈", "💪", "✨")

    DisposableEffect(Unit) {
        onDispose {
            voiceRecorder.stopRecording()
            voicePlayer.stop()
            pageTurnPlayer.release()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (storyToEdit == null) "New Story" else "Edit Story") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        tempLocationInput = location ?: ""
                        showLocationDialog = true
                        fetchCurrentLocation()
                    }) {
                        Icon(
                            imageVector = if (location.isNullOrBlank()) Icons.Default.AddLocationAlt else Icons.Default.LocationOn,
                            contentDescription = "Location",
                            tint = if (location.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showReminderDialog = true }) {
                        Icon(Icons.Default.Alarm, contentDescription = "Reminder")
                    }
                    IconButton(onClick = {
                        if (title.isBlank()) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Please enter a title for your story")
                            }
                            return@IconButton
                        }

                        val hasContent = pages.any { it.text.isNotBlank() } || 
                                images.isNotEmpty() || 
                                videos.isNotEmpty() || 
                                voiceNotes.isNotEmpty() ||
                                pageChecklists.values.any { it.isNotEmpty() }
                        
                        if (hasContent) {
                            val tags = tagsInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            val daysStr = selectedDays.joinToString(",") { it.name }
                            onSave(title.trim(), pages.map { it.text }, selectedMood, tags, images.toList(), videos.toList(), voiceNotes.toList(), timestamp, fontSize, reminderTimestamp, alarmTimestamp, textColor, repeatType, daysStr, location, Story.encodePageChecklists(pageChecklists), Story.encodePageStyles(pageStylesMap))
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar("Cannot save an empty story page")
                            }
                        }
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                },
            )
        },
        bottomBar = {
            if (focusedPageIndex != -1) {
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.imePadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            val pageContent = pages[focusedPageIndex]
                            val text = pageContent.text
                            val selection = pageContent.selection
                            val before = text.substring(0, selection.start)
                            val selected = text.substring(selection.start, selection.end)
                            val after = text.substring(selection.end)
                            val newText = "$before**$selected**$after"
                            pages[focusedPageIndex] = TextFieldValue(
                                text = newText,
                                selection = TextRange(selection.start + 2, selection.end + 2)
                            )
                        }) {
                            Icon(Icons.Default.FormatBold, contentDescription = "Bold")
                        }
                        
                        IconButton(onClick = { showTextEmojiPickerModal = true }) {
                            Icon(Icons.Default.SentimentSatisfiedAlt, contentDescription = "Insert Emoji")
                        }

                        IconButton(onClick = {
                            val currentP = if (focusedPageIndex != -1) focusedPageIndex else pagerState.currentPage
                            val isVisible = pageChecklistVisibilities[currentP] == true
                            pageChecklistVisibilities[currentP] = !isVisible
                        }) {
                            val currentP = if (focusedPageIndex != -1) focusedPageIndex else pagerState.currentPage
                            val hasItems = (pageChecklists[currentP] ?: emptyList()).isNotEmpty()
                            val isVisible = pageChecklistVisibilities[currentP] == true
                            Icon(
                                imageVector = Icons.Default.Checklist,
                                contentDescription = "To-Do & Checklist",
                                tint = if (hasItems || isVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = {
                            val pageContent = pages[focusedPageIndex]
                            val text = pageContent.text
                            val selection = pageContent.selection
                            val before = text.substring(0, selection.start)
                            val selected = text.substring(selection.start, selection.end)
                            val after = text.substring(selection.end)
                            val newText = "${before}_${selected}_${after}"
                            pages[focusedPageIndex] = TextFieldValue(
                                text = newText,
                                selection = TextRange(selection.start + 1, selection.end + 1)
                            )
                        }) {
                            Icon(Icons.Default.FormatItalic, contentDescription = "Italic")
                        }

                        // Modern Writable SP Font Size Pill Input (No pt/sp labels, high visibility)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .width(58.dp)
                                .height(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                androidx.compose.foundation.text.BasicTextField(
                                    value = customSpInput,
                                    onValueChange = { input ->
                                        if (input.length <= 3 && input.all { it.isDigit() }) {
                                            customSpInput = input
                                            val parsed = input.toIntOrNull()
                                            if (parsed != null && parsed in 6..120) {
                                                if (focusedPageIndex != -1 && focusedPageIndex < pages.size) {
                                                    val pageContent = pages[focusedPageIndex]
                                                    val text = pageContent.text
                                                    val currentSel = pageContent.selection
                                                    val savedSel = savedSelections[focusedPageIndex]
                                                    val selection = if (!currentSel.collapsed) currentSel else (savedSel ?: currentSel)
                                                    
                                                    if (!selection.collapsed && selection.end <= text.length) {
                                                        val rawSelected = text.substring(selection.start, selection.end)
                                                        val cleanSelected = rawSelected.replace(Regex("</?s(:[0-9]+)?>"), "")
                                                        val before = text.substring(0, selection.start)
                                                        val after = text.substring(selection.end)
                                                        val tagStart = "<s:$parsed>"
                                                        val tagEnd = "</s>"
                                                        val newText = "$before$tagStart$cleanSelected$tagEnd$after"
                                                        val newSelStart = selection.start
                                                        val newSelEnd = selection.start + tagStart.length + cleanSelected.length + tagEnd.length
                                                        pages[focusedPageIndex] = TextFieldValue(
                                                            text = newText,
                                                            selection = TextRange(newSelStart, newSelEnd)
                                                        )
                                                        savedSelections[focusedPageIndex] = TextRange(newSelStart, newSelEnd)
                                                    } else {
                                                        val pIdx = if (focusedPageIndex != -1) focusedPageIndex else pagerState.currentPage
                                                        val currStyle = pageStylesMap[pIdx] ?: PageStyle(fontSize = fontSize, textColor = textColor)
                                                        pageStylesMap[pIdx] = currStyle.copy(fontSize = parsed)
                                                        if (text.isNotEmpty()) {
                                                            val cleanText = text.replace(Regex("</?s(:[0-9]+)?>"), "")
                                                            val tagStart = "<s:$parsed>"
                                                            val tagEnd = "</s>"
                                                            val newText = "$tagStart$cleanText$tagEnd"
                                                            pages[focusedPageIndex] = TextFieldValue(
                                                                text = newText,
                                                                selection = TextRange(newText.length)
                                                            )
                                                        }
                                                    }
                                                } else {
                                                    fontSize = parsed
                                                }
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                    textStyle = TextStyle(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    ),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        VerticalDivider(modifier = Modifier.height(24.dp))

                        // All Colors Expanded Palette
                        val allColors = listOf(
                            Color.Black, Color.DarkGray, Color.Gray, Color(0xFFD32F2F),
                            Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7), Color(0xFF3F51B5),
                            Color(0xFF1976D2), Color(0xFF0288D1), Color(0xFF0097A7), Color(0xFF00796B),
                            Color(0xFF388E3C), Color(0xFF689F38), Color(0xFFAFB42B), Color(0xFFFBC02D),
                            Color(0xFFFFA000), Color(0xFFF57C00), Color(0xFFE64A19), Color(0xFF5D4037),
                            Color(0xFF455A64), Color(0xFF7B1FA2), Color(0xFF102A43)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                        ) {
                            IconButton(
                                onClick = { showCustomColorDialog = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Palette, contentDescription = "Color Palette", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }

                            LazyRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                items(allColors) { color ->
                                    val isSelected = textColor == color.toArgb()
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f),
                                                shape = CircleShape
                                            )
                                            .clickable { 
                                                if (focusedPageIndex != -1 && focusedPageIndex < pages.size) {
                                                    val pageContent = pages[focusedPageIndex]
                                                    val text = pageContent.text
                                                    val currentSel = pageContent.selection
                                                    val savedSel = savedSelections[focusedPageIndex]
                                                    val selection = if (!currentSel.collapsed) currentSel else (savedSel ?: currentSel)
                                                    
                                                    if (!selection.collapsed && selection.end <= text.length) {
                                                        val before = text.substring(0, selection.start)
                                                        val selected = text.substring(selection.start, selection.end)
                                                        val after = text.substring(selection.end)
                                                        val hex = String.format("%08X", color.toArgb())
                                                        val tagStart = "<c:$hex>"
                                                        val tagEnd = "</c>"
                                                        val newText = "$before$tagStart$selected$tagEnd$after"
                                                        pages[focusedPageIndex] = TextFieldValue(
                                                            text = newText,
                                                            selection = TextRange(selection.start + tagStart.length, selection.end + tagStart.length)
                                                        )
                                                        savedSelections[focusedPageIndex] = TextRange.Zero
                                                    } else {
                                                        val pIdx = if (focusedPageIndex != -1) focusedPageIndex else pagerState.currentPage
                                                        val currStyle = pageStylesMap[pIdx] ?: PageStyle(fontSize = fontSize, textColor = textColor)
                                                        pageStylesMap[pIdx] = currStyle.copy(textColor = color.toArgb())
                                                        if (text.isNotEmpty()) {
                                                            val hex = String.format("%08X", color.toArgb())
                                                            val cleanText = text.replace(Regex("</?c(:[0-9A-Fa-f]{8})?>"), "")
                                                            val tagStart = "<c:$hex>"
                                                            val tagEnd = "</c>"
                                                            val newText = "$tagStart$cleanText$tagEnd"
                                                            pages[focusedPageIndex] = TextFieldValue(
                                                                text = newText,
                                                                selection = TextRange(newText.length)
                                                            )
                                                        }
                                                    }
                                                } else {
                                                    val pIdx = pagerState.currentPage
                                                    val currStyle = pageStylesMap[pIdx] ?: PageStyle(fontSize = fontSize, textColor = textColor)
                                                    pageStylesMap[pIdx] = currStyle.copy(textColor = color.toArgb())
                                                }
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("How are you feeling?", style = MaterialTheme.typography.titleSmall)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(moods) { mood ->
                    FilterChip(
                        selected = selectedMood == mood,
                        onClick = { selectedMood = if (selectedMood == mood) null else mood },
                        label = { Text(mood, style = MaterialTheme.typography.headlineSmall) }
                    )
                }
                item {
                    AssistChip(
                        onClick = { showEmojiPickerModal = true },
                        label = { Text("😀 More", fontWeight = FontWeight.Bold) }
                    )
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth()
            )

            AnimatedVisibility(
                visible = !location.isNullOrBlank(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                val currentLoc = location
                if (!currentLoc.isNullOrBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            onClick = {
                                tempLocationInput = currentLoc
                                showLocationDialog = true
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.LocationOn,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onTertiary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "LOCATION",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            text = currentLoc,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        location = null
                                        isRealTimeTracking = false
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove Location",
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        InAppMapCard(
                            location = currentLoc,
                            onClickExpand = { showInAppMapDialog = true }
                        )
                    }
                }
            }



            OutlinedTextField(
                value = tagsInput,
                onValueChange = { tagsInput = it },
                label = { Text("Tags (comma separated)") },
                modifier = Modifier.fillMaxWidth()
            )

            var lastTriggeredTarget by remember { mutableIntStateOf(-1) }
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.targetPage }.collect { target ->
                    if (pagerState.isScrollInProgress && target != pagerState.currentPage && target != lastTriggeredTarget) {
                        playPageTurn()
                        lastTriggeredTarget = target
                    } else if (!pagerState.isScrollInProgress) {
                        lastTriggeredTarget = -1
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                pageSpacing = 12.dp,
                beyondViewportPageCount = 1
            ) { pageIndex ->
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 8.dp)
                        .graphicsLayer {
                            val pageOffset = ((pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction)
                            val isLeftPage = pageOffset > 0
                            transformOrigin = TransformOrigin(if (isLeftPage) { 1f } else { 0f }, 0.5f)
                            rotationY = -25f * pageOffset.coerceIn(-1f, 1f)
                            val scale = lerp(0.95f, 1f, 1f - pageOffset.absoluteValue.coerceIn(0f, 1f))
                            scaleX = scale
                            scaleY = scale
                            alpha = lerp(0.7f, 1f, 1f - pageOffset.absoluteValue.coerceIn(0f, 1f))
                            cameraDistance = 12 * density
                        },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9E3)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Page ${pageIndex + 1}", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                                IconButton(onClick = { showFullScreenPages = true }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Fullscreen, contentDescription = "Full Screen", modifier = Modifier.size(16.dp), tint = Color.Gray)
                                }
                            }
                            if (pages.size > 1) {
                                IconButton(onClick = { 
                                    if (focusedPageIndex == pageIndex) focusedPageIndex = -1
                                    pages.removeAt(pageIndex) 
                                }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Page", modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        // Per-Page Checklist Section
                        val pageItems = pageChecklists[pageIndex] ?: emptyList()
                        val isPageChecklistVisible = pageChecklistVisibilities[pageIndex] == true || pageItems.isNotEmpty()

                        if (isPageChecklistVisible) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    if (pageItems.isNotEmpty()) {
                                        pageItems.forEachIndexed { index, item ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 1.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = item.isChecked,
                                                    onCheckedChange = { checked ->
                                                        val updated = pageItems.toMutableList()
                                                        updated[index] = item.copy(isChecked = checked)
                                                        pageChecklists[pageIndex] = updated
                                                    },
                                                    modifier = Modifier.graphicsLayer { scaleX = 0.85f; scaleY = 0.85f }
                                                )
                                                Text(
                                                    text = item.text,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (item.isChecked) Color.Gray else Color(textColor),
                                                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(
                                                    onClick = {
                                                        val updated = pageItems.toMutableList()
                                                        updated.removeAt(index)
                                                        pageChecklists[pageIndex] = updated
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Delete Task",
                                                        modifier = Modifier.size(14.dp),
                                                        tint = MaterialTheme.colorScheme.outline
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = newChecklistItemText,
                                            onValueChange = { newChecklistItemText = it },
                                            placeholder = { Text("Add page ${pageIndex + 1} task...", fontSize = 12.sp) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(10.dp),
                                            textStyle = TextStyle(fontSize = 12.sp),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Button(
                                            onClick = {
                                                val trimmed = newChecklistItemText.trim()
                                                if (trimmed.isNotBlank()) {
                                                    val current = pageChecklists[pageIndex] ?: emptyList()
                                                    pageChecklists[pageIndex] = current + ChecklistItem(text = trimmed)
                                                    newChecklistItemText = ""
                                                }
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            enabled = newChecklistItemText.isNotBlank(),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                        
                        val curPageStyle = pageStylesMap[pageIndex] ?: PageStyle(fontSize = fontSize, textColor = textColor)
                        val curFontSize = curPageStyle.fontSize
                        val curTextColor = curPageStyle.textColor

                        OutlinedTextField(
                            value = pages[pageIndex],
                            onValueChange = { newValue ->
                                pages[pageIndex] = newValue
                                if (!newValue.selection.collapsed) {
                                    savedSelections[pageIndex] = newValue.selection
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 180.dp)
                                .onFocusChanged { if (it.isFocused) focusedPageIndex = pageIndex },
                            minLines = 6,
                            maxLines = Int.MAX_VALUE,
                            visualTransformation = MarkdownUtils.markdownTransformation,
                            textStyle = TextStyle(fontSize = curFontSize.sp, lineHeight = (curFontSize * 1.5).sp, color = Color(curTextColor)),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                        
                        Text(
                            text = "Words: ${if (pages[pageIndex].text.isEmpty()) 0 else pages[pageIndex].text.trim().split("\\s+".toRegex()).size}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.End),
                            color = Color.Gray
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = {
                        pages.add(TextFieldValue(""))
                        scope.launch {
                            pagerState.animateScrollToPage(pages.size - 1)
                        }
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.NoteAdd,
                            contentDescription = "Add Page",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Text("Voice Journal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Recording Controller Card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isRecording) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isRecording) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // Mic Record Button with Pulse Effect
                            val pulseScale by rememberInfiniteTransition(label = "pulse").animateFloat(
                                initialValue = 1f,
                                targetValue = 1.18f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(800, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "mic_pulse"
                            )

                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .graphicsLayer {
                                        if (isRecording) {
                                            scaleX = pulseScale
                                            scaleY = pulseScale
                                        }
                                    }
                                    .clip(CircleShape)
                                    .background(
                                        if (isRecording) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.primary
                                    )
                                    .clickable {
                                        if (isRecording) {
                                            voiceRecorder.stopRecording()
                                            isRecording = false
                                        } else {
                                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                                val file = File(context.filesDir, "voice_${System.currentTimeMillis()}.mp4")
                                                voiceRecorder.startRecording(file)
                                                voiceNotes.add(file.absolutePath)
                                                isRecording = true
                                            } else {
                                                voicePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = if (isRecording) "Stop" else "Record",
                                    tint = if (isRecording) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isRecording) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.error)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "REC  $formattedTimer",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    } else {
                                        Text(
                                            text = "Record Audio Note",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                Text(
                                    text = if (isRecording) "Tap red button to finish" else "${voiceNotes.size} voice notes recorded",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Live Waveform visualizer while recording
                        if (isRecording) {
                            AudioWaveformBars(
                                isPlaying = true,
                                barCount = 8,
                                activeColor = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .width(48.dp)
                                    .height(24.dp)
                            )
                        }
                    }
                }

                // Voice Note Cards Row
                if (voiceNotes.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        itemsIndexed(voiceNotes) { index, path ->
                            val isPlaying = currentlyPlayingVoice == path
                            ModernVoiceNoteCard(
                                index = index,
                                isPlaying = isPlaying,
                                onPlayToggle = {
                                    if (isPlaying) {
                                        voicePlayer.stop()
                                        currentlyPlayingVoice = null
                                    } else {
                                        currentlyPlayingVoice = path
                                        scope.launch {
                                            val file = withContext(Dispatchers.IO) {
                                                FileEncryptionManager.decryptToTempFile(context, File(path))
                                            }
                                            if (file != null) {
                                                voicePlayer.play(file) {
                                                    currentlyPlayingVoice = null
                                                    file.delete()
                                                }
                                            }
                                        }
                                    }
                                },
                                onDelete = {
                                    if (isPlaying) {
                                        voicePlayer.stop()
                                        currentlyPlayingVoice = null
                                    }
                                    voiceNotes.remove(path)
                                    File(path).delete()
                                }
                            )
                        }
                    }
                }
            }

            Text("Photos & Videos", style = MaterialTheme.typography.titleSmall)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable {
                                val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                                val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                if (hasCamera && hasAudio) {
                                    showCamera = true
                                } else {
                                    cameraPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                                }
                            }
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = "Dual Camera")
                            Text("Dual", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                item {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { imagePickerLauncher.launch("image/*") }
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.AddAPhoto, contentDescription = "Add Photo") }
                }
                item {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { videoPickerLauncher.launch("video/*") }
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.VideoCall, contentDescription = "Add Video") }
                }
                itemsIndexed(images) { index, imagePath ->
                    Box {
                        SecureEditorThumbnail(path = imagePath, isVideo = false, onClick = { fullScreenMediaIndex = index })
                        IconButton(onClick = { images.remove(imagePath) }, modifier = Modifier.align(Alignment.TopEnd)) { Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error) }
                    }
                }
                itemsIndexed(videos) { index, videoPath ->
                    Box(modifier = Modifier.size(100.dp)) {
                        Box(modifier = Modifier.clickable { fullScreenMediaIndex = images.size + index }) {
                            SecureEditorThumbnail(path = videoPath, isVideo = true, onClick = { fullScreenMediaIndex = images.size + index })
                        }
                        IconButton(onClick = { videos.remove(videoPath) }, modifier = Modifier.align(Alignment.TopEnd)) { Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }

    if (showCamera) {
        DualCameraScreen(
            onMediaCaptured = { path, isVideo ->
                if (isVideo) videos.add(path) else images.add(path)
                showCamera = false
            },
            onDismiss = { showCamera = false }
        )
    }

    fullScreenMediaIndex?.let { index ->
        MediaFullScreenPreview(allMedia = allMedia, initialIndex = index, onDismiss = { fullScreenMediaIndex = null })
    }

    if (showFullScreenPages) {
        Dialog(onDismissRequest = { showFullScreenPages = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            val offsetY = remember { Animatable(0f) }
            Surface(
                modifier = Modifier.fillMaxSize().offset { IntOffset(0, offsetY.value.roundToInt()) }.draggable(orientation = Orientation.Vertical, state = rememberDraggableState { delta -> val newOffset = offsetY.value + delta; if (newOffset > 0) { scope.launch { offsetY.snapTo(newOffset) } } }, onDragStopped = { velocity -> if (offsetY.value > 300f || velocity > 1000f) { showFullScreenPages = false } else { scope.launch { offsetY.animateTo(0f) } } }),
                color = Color.Black.copy(alpha = (0.95f - (offsetY.value / 1000f)).coerceIn(0f, 0.95f))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val fullPagerState = rememberPagerState(initialPage = pagerState.currentPage, pageCount = { pages.size })
                    var lastTriggeredFullTarget by remember { mutableIntStateOf(-1) }
                    LaunchedEffect(fullPagerState) { snapshotFlow { fullPagerState.targetPage }.collect { target -> if (fullPagerState.isScrollInProgress && target != fullPagerState.currentPage && target != lastTriggeredFullTarget) { playPageTurn(); lastTriggeredFullTarget = target; pagerState.scrollToPage(target) } else if (!fullPagerState.isScrollInProgress) { lastTriggeredFullTarget = -1 } } }
                    HorizontalPager(state = fullPagerState, modifier = Modifier.fillMaxSize(), pageSpacing = 0.dp, beyondViewportPageCount = 1) { pageIndex ->
                        Card(
                            modifier = Modifier.fillMaxSize().padding(16.dp).graphicsLayer { val pageOffset = ((fullPagerState.currentPage - pageIndex) + fullPagerState.currentPageOffsetFraction); val isLeftPage = pageOffset > 0; transformOrigin = TransformOrigin(if (isLeftPage) { 1f } else { 0f }, 0.5f); rotationY = -25f * pageOffset.coerceIn(-1f, 1f); val scale = lerp(0.9f, 1f, 1f - pageOffset.absoluteValue.coerceIn(0f, 1f)); scaleX = scale; scaleY = scale; alpha = lerp(0.6f, 1f, 1f - pageOffset.absoluteValue.coerceIn(0f, 1f)); cameraDistance = 12 * density },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9E3)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState())) {
                                Text(text = "Reading Mode - Page ${pageIndex + 1} of ${pages.size}", style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.align(Alignment.End))

                                val fullPageItems = pageChecklists[pageIndex] ?: emptyList()
                                val isFullPageChecklistVisible = pageChecklistVisibilities[pageIndex] == true || fullPageItems.isNotEmpty()

                                if (isFullPageChecklistVisible) {
                                    Spacer(Modifier.height(10.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.Transparent,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            if (fullPageItems.isNotEmpty()) {
                                                fullPageItems.forEachIndexed { index, item ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 1.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Checkbox(
                                                            checked = item.isChecked,
                                                            onCheckedChange = { checked ->
                                                                val updated = fullPageItems.toMutableList()
                                                                updated[index] = item.copy(isChecked = checked)
                                                                pageChecklists[pageIndex] = updated
                                                            },
                                                            modifier = Modifier.graphicsLayer { scaleX = 0.85f; scaleY = 0.85f }
                                                        )
                                                        Text(
                                                            text = item.text,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = if (item.isChecked) Color.Gray else Color(textColor),
                                                            textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(16.dp))
                                Text(text = MarkdownUtils.parseMarkdown(pages[pageIndex].text), style = MaterialTheme.typography.bodyLarge.copy(fontSize = (fontSize + 2).sp, lineHeight = ((fontSize + 2) * 1.6).sp))
                            }
                        }
                    }
                    IconButton(onClick = { showFullScreenPages = false }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).padding(top = 32.dp)) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White) }
                }
            }
        }
    }

    if (showReminderDialog) {
        var tempReminderTime by remember {
            val millis = reminderTimestamp ?: alarmTimestamp ?: System.currentTimeMillis()
            mutableStateOf(LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).toLocalTime())
        }
        var isAlarmMode by remember { mutableStateOf(alarmTimestamp != null) }

        AlertDialog(
            onDismissRequest = { showReminderDialog = false },
            title = { Text("Story Reminder") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Time (HH:mm)")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        var hourStr by remember { mutableStateOf(tempReminderTime.hour.toString().padStart(2, '0')) }
                        var minuteStr by remember { mutableStateOf(tempReminderTime.minute.toString().padStart(2, '0')) }
                        
                        OutlinedTextField(
                            value = hourStr,
                            onValueChange = { 
                                if (it.length <= 2 && it.all { c -> c.isDigit() }) {
                                    hourStr = it
                                    it.toIntOrNull()?.let { h -> if (h < 24) tempReminderTime = LocalTime.of(h, tempReminderTime.minute) }
                                }
                            },
                            modifier = Modifier.width(60.dp),
                            singleLine = true
                        )
                        Text(" : ", fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = minuteStr,
                            onValueChange = { 
                                if (it.length <= 2 && it.all { c -> c.isDigit() }) {
                                    minuteStr = it
                                    it.toIntOrNull()?.let { m -> if (m < 60) tempReminderTime = LocalTime.of(tempReminderTime.hour, m) }
                                }
                            },
                            modifier = Modifier.width(60.dp),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { isAlarmMode = !isAlarmMode }
                    ) {
                        Checkbox(checked = isAlarmMode, onCheckedChange = { isAlarmMode = it })
                        Text("Set as Alarm")
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("Frequency", style = MaterialTheme.typography.labelLarge)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = repeatType == "ONCE",
                            onClick = { repeatType = "ONCE" },
                            label = { Text("Once") }
                        )
                        FilterChip(
                            selected = repeatType == "WEEKLY",
                            onClick = { repeatType = "WEEKLY" },
                            label = { Text("Repeat") }
                        )
                    }
                    
                    if (repeatType == "WEEKLY") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Repeat on", style = MaterialTheme.typography.labelMedium)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            DayOfWeek.entries.forEach { day ->
                                val isSelected = selectedDays.contains(day)
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            selectedDays = if (isSelected) { selectedDays - day } else { selectedDays + day }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = day.name.take(1),
                                        color = if (isSelected) { MaterialTheme.colorScheme.onPrimary } else { MaterialTheme.colorScheme.onSurfaceVariant },
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(
                                onClick = { selectedDays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY) },
                                label = { Text("Weekdays", fontSize = 10.sp) }
                            )
                            AssistChip(
                                onClick = { selectedDays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) },
                                label = { Text("Weekends", fontSize = 10.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val date = if (storyToEdit != null) { LocalDate.ofInstant(Instant.ofEpochMilli(storyToEdit.timestamp), ZoneId.systemDefault()) } else { initialDate ?: LocalDate.now() }
                    val combined = date.atTime(tempReminderTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    if (isAlarmMode) {
                        alarmTimestamp = combined
                        reminderTimestamp = null
                    } else {
                        reminderTimestamp = combined
                        alarmTimestamp = null
                    }
                    showReminderDialog = false
                }) { Text("Done") }
            },
            dismissButton = {
                TextButton(onClick = { 
                    reminderTimestamp = null
                    alarmTimestamp = null
                    repeatType = "ONCE"
                    selectedDays = emptySet()
                    showReminderDialog = false 
                }) { Text("Remove") }
            }
        )
    }

    if (showLocationDialog) {
        AlertDialog(
            onDismissRequest = { showLocationDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Story Location")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { fetchCurrentLocation() },
                        enabled = !isFetchingLocation,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isFetchingLocation) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Detecting Phone Location...")
                        } else {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Use Current Phone Location")
                        }
                    }

                    OutlinedTextField(
                        value = tempLocationInput,
                        onValueChange = { tempLocationInput = it },
                        label = { Text("Location / City / Place") },
                        placeholder = { Text("e.g. Istanbul, Paris, Central Park...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Quick Suggestions", style = MaterialTheme.typography.labelMedium)

                    val suggestions = listOf("🏠 Home", "💼 Office", "☕ Cafe", "🌳 Park", "✈️ Travel", "🏖️ Beach")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(suggestions) { sugg ->
                            FilterChip(
                                selected = tempLocationInput == sugg,
                                onClick = { tempLocationInput = sugg },
                                label = { Text(sugg) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    location = tempLocationInput.ifBlank { null }
                    showLocationDialog = false
                }) {
                    Text("Set Location")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    location = null
                    tempLocationInput = ""
                    showLocationDialog = false
                }) {
                    Text("Remove")
                }
            }
        )
    }

    if (showInAppMapDialog && !location.isNullOrBlank()) {
        InAppLocationDialog(
            location = location!!,
            onDismiss = { showInAppMapDialog = false }
        )
    }

    if (showEmojiPickerModal) {
        AndroidEmojiPickerModal(
            onEmojiSelected = { emoji ->
                selectedMood = emoji
            },
            onDismiss = { showEmojiPickerModal = false }
        )
    }

    if (showTextEmojiPickerModal) {
        AndroidEmojiPickerModal(
            onEmojiSelected = { emoji ->
                if (focusedPageIndex != -1 && focusedPageIndex < pages.size) {
                    val pageContent = pages[focusedPageIndex]
                    val text = pageContent.text
                    val selection = pageContent.selection
                    val before = text.substring(0, selection.start)
                    val after = text.substring(selection.end)
                    val newText = "$before$emoji$after"
                    pages[focusedPageIndex] = TextFieldValue(
                        text = newText,
                        selection = TextRange(selection.start + emoji.length)
                    )
                }
            },
            onDismiss = { showTextEmojiPickerModal = false }
        )
    }

    if (showCustomColorDialog) {
        RealPaletteDialog(
            initialColor = Color(textColor),
            onColorSelected = { color ->
                textColor = color.toArgb()
                if (focusedPageIndex != -1 && focusedPageIndex < pages.size) {
                    val pageContent = pages[focusedPageIndex]
                    val text = pageContent.text
                    val currentSel = pageContent.selection
                    val savedSel = savedSelections[focusedPageIndex]
                    val selection = if (!currentSel.collapsed) currentSel else (savedSel ?: currentSel)

                    if (!selection.collapsed && selection.end <= text.length) {
                        val before = text.substring(0, selection.start)
                        val selected = text.substring(selection.start, selection.end)
                        val after = text.substring(selection.end)
                        val hex = String.format("%08X", color.toArgb())
                        val tagStart = "<c:$hex>"
                        val tagEnd = "</c>"
                        val newText = "$before$tagStart$selected$tagEnd$after"
                        pages[focusedPageIndex] = TextFieldValue(
                            text = newText,
                            selection = TextRange(selection.start + tagStart.length, selection.end + tagStart.length)
                        )
                        savedSelections[focusedPageIndex] = TextRange.Zero
                    } else if (text.isNotEmpty()) {
                        val hex = String.format("%08X", color.toArgb())
                        val cleanText = text.replace(Regex("</?c(:[0-9A-Fa-f]{8})?>"), "")
                        val tagStart = "<c:$hex>"
                        val tagEnd = "</c>"
                        val newText = "$tagStart$cleanText$tagEnd"
                        pages[focusedPageIndex] = TextFieldValue(
                            text = newText,
                            selection = TextRange(newText.length)
                        )
                    }
                }
            },
            onDismiss = { showCustomColorDialog = false }
        )
    }
}

@Composable
fun RealPaletteDialog(
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedColor by remember { mutableStateOf(initialColor) }
    var hexInput by remember { mutableStateOf(String.format("#%06X", 0xFFFFFF and initialColor.toArgb())) }

    val paletteSwatches = listOf(
        Color(0xFFFFB7B2), Color(0xFFFFDAC1), Color(0xFFE2F0CB), Color(0xFFB5EAD7), Color(0xFFC7CEEA), Color(0xFFF3C4FB),
        Color(0xFFFF1744), Color(0xFFF50057), Color(0xFFD500F9), Color(0xFF651FFF), Color(0xFF3D5AFE), Color(0xFF2979FF),
        Color(0xFF00E5FF), Color(0xFF1DE9B6), Color(0xFF00E676), Color(0xFF76FF03), Color(0xFFFFEA00), Color(0xFFFF9100),
        Color(0xFF3E2723), Color(0xFF4E342E), Color(0xFF37474F), Color(0xFF263238), Color(0xFF1A237E), Color(0xFF004D40)
    )

    var hue by remember {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), hsv)
        mutableFloatStateOf(hsv[0])
    }
    var saturation by remember {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), hsv)
        mutableFloatStateOf(hsv[1])
    }
    var value by remember {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), hsv)
        mutableFloatStateOf(hsv[2])
    }

    fun updateHSV(h: Float = hue, s: Float = saturation, v: Float = value) {
        hue = h
        saturation = s
        value = v
        val argb = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
        selectedColor = Color(argb)
        hexInput = String.format("#%06X", 0xFFFFFF and argb)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Color Palette Studio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = selectedColor,
                    border = BorderStroke(2.dp, Color.White),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f))
                                )
                            )
                    ) {
                        Text(
                            text = hexInput.uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 2.sp
                        )
                    }
                }

                Text(
                    text = "Artist Palette Swatches",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(6),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                ) {
                    items(paletteSwatches.size) { idx ->
                        val color = paletteSwatches[idx]
                        val isSelected = selectedColor == color
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
                                    shape = CircleShape
                                )
                                .clickable {
                                    selectedColor = color
                                    hexInput = String.format("#%06X", 0xFFFFFF and color.toArgb())
                                    val hsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
                                    hue = hsv[0]
                                    saturation = hsv[1]
                                    value = hsv[2]
                                }
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Hue Spectrum", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text("${hue.toInt()}°", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = hue,
                        onValueChange = { updateHSV(h = it) },
                        valueRange = 0f..360f
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Saturation", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text("${(saturation * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = saturation,
                        onValueChange = { updateHSV(s = it) },
                        valueRange = 0f..1f
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onColorSelected(selectedColor)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Apply Color", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
