package com.encryptedjournal.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaMetadataRetriever
import android.media.SoundPool
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.util.Patterns
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.encryptedjournal.data.Story
import com.encryptedjournal.data.Reminder
import com.encryptedjournal.data.FileEncryptionManager
import com.encryptedjournal.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    viewModel: StoryViewModel,
    onAddStory: (LocalDate?) -> Unit,
    onStoryClick: (Story) -> Unit,
    onEditStory: (Story) -> Unit,
) {
    val stories by viewModel.stories.collectAsState()
    val reminders by viewModel.reminders.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showRssFeed by remember { mutableStateOf(false) }
    var showCalculator by remember { mutableStateOf(false) }
    var showTranslator by remember { mutableStateOf(false) }
    var storyToDelete by remember { mutableStateOf<Story?>(null) }
    var locationToViewInApp by remember { mutableStateOf<String?>(null) }
    var fullScreenMediaInfo by remember { mutableStateOf<Pair<List<Pair<String, Boolean>>, Int>?>(null) }

    // Calendar state
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var calendarViewMode by remember { mutableStateOf(CalendarViewMode.DAYS) }
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]

    // Reminder state
    var showReminderDialog by remember { mutableStateOf(false) }
    var reminderToEdit by remember { mutableStateOf<Reminder?>(null) }
    var reminderToDelete by remember { mutableStateOf<Reminder?>(null) }
    var reminderTitle by remember { mutableStateOf("") }
    var reminderTime by remember { mutableStateOf(LocalTime.now()) }
    var isAlarm by remember { mutableStateOf(false) }
    var repeatType by remember { mutableStateOf("ONCE") }
    var selectedDays by remember { mutableStateOf(setOf<DayOfWeek>()) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // SoundPool for low-latency sound effects
    val soundPool = remember {
        SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .build()
            )
            .build()
    }
    val soundId = remember { mutableIntStateOf(0) }
    
    LaunchedEffect(Unit) {
        soundId.intValue = soundPool.load(context, R.raw.page_flipc, 1)
    }
    
    DisposableEffect(Unit) {
        onDispose {
            soundPool.release()
        }
    }

    // Fixed anchor for pager stability
    val anchorMonth = remember { YearMonth.of(2024, 1) }
    val pageCount = 2000
    val initialPage = remember {
        val now = YearMonth.now()
        (now.year - anchorMonth.year) * 12 + (now.monthValue - anchorMonth.monthValue)
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { pageCount }

    // Sync currentMonth with pager and play sound
    var isFirstLoad by remember { mutableStateOf(true) }
    LaunchedEffect(pagerState.currentPage) {
        currentMonth = anchorMonth.plusMonths(pagerState.currentPage.toLong())
        
        if (!isFirstLoad) {
            if (soundId.intValue != 0) {
                soundPool.play(soundId.intValue, 1f, 1f, 1, 0, 1f)
            } else {
                Log.e("CalendarSound", "Sound not loaded yet")
                // Try playing with MediaPlayer as fallback
                try {
                    MediaPlayer.create(context, R.raw.page_flipc)?.apply {
                        setOnCompletionListener { it.release() }
                        start()
                    }
                } catch (e: Exception) {
                    Log.e("CalendarSound", "Fallback MediaPlayer failed", e)
                }
            }
        }
        isFirstLoad = false
    }

    // QR state
    var showQrOptions by remember { mutableStateOf(false) }
    var showQrGenerator by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    var generatedQrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var qrContentToGenerate by remember { mutableStateOf("") }
    var scannedContent by remember { mutableStateOf<String?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showQrScanner = true
        } else {
            Toast.makeText(context, "Camera permission required to scan", Toast.LENGTH_SHORT).show()
        }
    }

    val bottomSheetState = rememberModalBottomSheetState()

    LaunchedEffect(reminderToEdit) {
        if (reminderToEdit != null) {
            reminderTitle = reminderToEdit!!.title
            reminderTime = Date(reminderToEdit!!.timestamp).toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
            isAlarm = reminderToEdit!!.isAlarm
            repeatType = reminderToEdit!!.repeatType
            selectedDays = reminderToEdit!!.repeatDays.split(",")
                .filter { it.isNotEmpty() }
                .map { DayOfWeek.valueOf(it) }
                .toSet()
        } else {
            reminderTitle = ""
            reminderTime = LocalTime.now()
            isAlarm = false
            repeatType = "ONCE"
            selectedDays = setOf()
        }
    }

    val storiesByDate = remember(stories) {
        stories.groupBy {
            val date = Date(it.timestamp)
            date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        }
    }

    val remindersByDate = remember(reminders) {
        reminders.groupBy {
            val date = Date(it.timestamp)
            date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        }
    }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            focusRequester.requestFocus()
        }
    }

    val isSearching = searchQuery.isNotBlank()

    val filteredStories = remember(stories, searchQuery, selectedDate, locale) {
        stories.filter { story ->
            val storyDate = Date(story.timestamp).toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
            val formattedDateStr = storyDate.format(DateTimeFormatter.ofPattern("EEEE MMMM d yyyy d MMMM yyyy", locale))

            val matchesSearch = !isSearching ||
                    story.title.contains(searchQuery, ignoreCase = true) ||
                    story.pages.any { page -> page.contains(searchQuery, ignoreCase = true) } ||
                    story.tags.any { tag -> tag.contains(searchQuery, ignoreCase = true) } ||
                    story.mood?.contains(searchQuery, ignoreCase = true) == true ||
                    formattedDateStr.contains(searchQuery, ignoreCase = true)

            // When searching globally, search across ALL dates
            val matchesDate = isSearching || selectedDate == null || storyDate == selectedDate

            matchesSearch && matchesDate
        }.sortedByDescending { it.timestamp }
    }

    val filteredReminders = remember(reminders, searchQuery) {
        if (isSearching) {
            reminders.filter { reminder ->
                reminder.title.contains(searchQuery, ignoreCase = true)
            }
        } else {
            emptyList()
        }
    }

    Scaffold(
        topBar = {
            if (isSearchExpanded) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding(),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            isSearchExpanded = false
                            searchQuery = ""
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search title, text, tags...") },
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 4.dp)
                                .focusRequester(focusRequester),
                            singleLine = true,
                            shape = MaterialTheme.shapes.extraLarge,
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear")
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                    }
                }
            } else {
                TopAppBar(
                    title = { Text("My Journal") },
                    actions = {
                        IconButton(onClick = { isSearchExpanded = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = { showRssFeed = true }) {
                            Icon(Icons.Default.RssFeed, contentDescription = "RSS Reader")
                        }
                        IconButton(onClick = { showCalculator = true }) {
                            Icon(Icons.Default.Calculate, contentDescription = "Scientific Calculator")
                        }
                        IconButton(onClick = { showTranslator = true }) {
                            Icon(Icons.Default.Translate, contentDescription = "Offline Translator")
                        }
                        IconButton(onClick = { showReminderDialog = true }) {
                            Icon(Icons.Default.Alarm, contentDescription = "Add Reminder")
                        }
                        IconButton(onClick = { showQrOptions = true }) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "QR Options")
                        }
                    }
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    CalendarHeader(
                        currentMonth = currentMonth,
                        locale = locale,
                        viewMode = calendarViewMode,
                        onTodayClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(initialPage)
                            }
                            selectedDate = LocalDate.now()
                            calendarViewMode = CalendarViewMode.DAYS
                        },
                        onTitleClick = {
                            calendarViewMode = when(calendarViewMode) {
                                CalendarViewMode.DAYS -> CalendarViewMode.MONTHS
                                CalendarViewMode.MONTHS -> CalendarViewMode.YEARS
                                CalendarViewMode.YEARS -> CalendarViewMode.DAYS
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    when(calendarViewMode) {
                        CalendarViewMode.DAYS -> {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxWidth(),
                                key = { it }
                            ) { page ->
                                val monthForPage = anchorMonth.plusMonths(page.toLong())

                                Box(
                                    modifier = Modifier
                                        .graphicsLayer {
                                            val pageOffset = (
                                                    (pagerState.currentPage - page) + pagerState
                                                        .currentPageOffsetFraction
                                                    ).coerceIn(-1f, 1f)
                                            
                                            // Cube transformation refinement
                                            cameraDistance = 12 * density
                                            rotationY = 90f * pageOffset
                                            
                                            transformOrigin = TransformOrigin(
                                                pivotFractionX = if (pageOffset > 0f) 0f else 1f,
                                                pivotFractionY = 0.5f
                                            )
                                            
                                            // Keeps the edges connected during rotation
                                            translationX = -pageOffset * size.width
                                            
                                            alpha = lerp(
                                                start = 1f,
                                                stop = 0.6f,
                                                fraction = pageOffset.absoluteValue
                                            )
                                        }
                                ) {
                                    CalendarGrid(
                                        currentMonth = monthForPage,
                                        selectedDate = selectedDate,
                                        storiesByDate = storiesByDate,
                                        remindersByDate = remindersByDate,
                                        onDateSelected = { 
                                            selectedDate = if (selectedDate == it) null else it 
                                        }
                                    )
                                }
                            }
                        }
                        CalendarViewMode.MONTHS -> {
                            MonthPicker(
                                currentYear = currentMonth.year,
                                onMonthSelected = { month ->
                                    val targetMonth = YearMonth.of(currentMonth.year, month)
                                    val monthsDiff = ((targetMonth.year - anchorMonth.year) * 12 + (targetMonth.monthValue - anchorMonth.monthValue))
                                    scope.launch {
                                        pagerState.scrollToPage(monthsDiff)
                                    }
                                    calendarViewMode = CalendarViewMode.DAYS
                                },
                                locale = locale
                            )
                        }
                        CalendarViewMode.YEARS -> {
                            YearPicker(
                                currentYear = currentMonth.year,
                                onYearSelected = { year ->
                                    val targetMonth = YearMonth.of(year, currentMonth.month)
                                    val monthsDiff = ((targetMonth.year - anchorMonth.year) * 12 + (targetMonth.monthValue - anchorMonth.monthValue))
                                    scope.launch {
                                        pagerState.scrollToPage(monthsDiff)
                                    }
                                    calendarViewMode = CalendarViewMode.MONTHS
                                }
                            )
                        }
                    }
                }
            }

            if (isSearching) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Global Search (${filteredStories.size + filteredReminders.size} results)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = { searchQuery = "" }) {
                        Text("Clear")
                    }
                }
            } else if (selectedDate != null) {
                val selectedDateReminders = remindersByDate[selectedDate] ?: emptyList()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "For ${selectedDate!!.dayOfMonth} ${selectedDate!!.month.name.lowercase().replaceFirstChar { it.uppercase() }}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row {
                        IconButton(onClick = { onAddStory(selectedDate) }) {
                            Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = "Create Story for this day", tint = MaterialTheme.colorScheme.primary)
                        }
                        TextButton(onClick = { selectedDate = null }) {
                            Text("Show All")
                        }
                    }
                }

                if (selectedDateReminders.isNotEmpty()) {
                    Text(
                        text = "Reminders",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    selectedDateReminders.forEach { reminder ->
                        val time = remember(reminder.timestamp, locale) {
                            SimpleDateFormat("HH:mm", locale).format(Date(reminder.timestamp))
                        }
                        SwipeableReminderItem(
                            reminder = reminder,
                            timeText = time,
                            onEdit = { reminderToEdit = it; showReminderDialog = true },
                            onDeleteRequest = { reminderToDelete = it },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (filteredStories.isEmpty() && filteredReminders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    val message = if (isSearching) {
                        "No matches found for \"$searchQuery\"."
                    } else if (selectedDate != null) {
                        "No stories for this day."
                    } else {
                        "No stories found."
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(message)
                        if (selectedDate != null && !isSearching) {
                            Spacer(Modifier.height(12.dp))
                            Surface(
                                onClick = { onAddStory(selectedDate) },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                shadowElevation = 4.dp,
                                modifier = Modifier.size(52.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.NoteAdd,
                                        contentDescription = "Write about this day",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isSearching && filteredReminders.isNotEmpty()) {
                        item {
                            Text(
                                text = "Reminders (${filteredReminders.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(filteredReminders, key = { "rem_${it.id}" }) { reminder ->
                            val time = remember(reminder.timestamp, locale) {
                                SimpleDateFormat("HH:mm - d MMM yyyy", locale).format(Date(reminder.timestamp))
                            }
                            SwipeableReminderItem(
                                reminder = reminder,
                                timeText = time,
                                onEdit = { reminderToEdit = it; showReminderDialog = true },
                                onDeleteRequest = { reminderToDelete = it }
                            )
                        }
                        if (filteredStories.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Stories (${filteredStories.size})",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    items(filteredStories, key = { it.id }) { story ->
                        val dismissState = rememberSwipeToDismissBoxState()
                        LaunchedEffect(dismissState.currentValue) {
                            when (dismissState.currentValue) {
                                SwipeToDismissBoxValue.EndToStart -> {
                                    storyToDelete = story
                                    dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                                }
                                SwipeToDismissBoxValue.StartToEnd -> {
                                    onEditStory(story)
                                    dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                                }
                                SwipeToDismissBoxValue.Settled -> {}
                            }
                        }

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = true,
                            enableDismissFromEndToStart = true,
                            backgroundContent = {
                                val color = when (dismissState.dismissDirection) {
                                    SwipeToDismissBoxValue.StartToEnd -> Color(0xFF4CAF50)
                                    SwipeToDismissBoxValue.EndToStart -> Color(0xFFF44336)
                                    else -> Color.Transparent
                                }
                                val alignment = when (dismissState.dismissDirection) {
                                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                    SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                                    else -> Alignment.Center
                                }

                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .clip(CardDefaults.shape)
                                        .background(color)
                                        .padding(horizontal = 24.dp),
                                    contentAlignment = alignment
                                ) {
                                    if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                                        Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White)
                                    } else if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
                                    }
                                }
                            }
                        ) {
                            StoryItem(
                                story = story,
                                onStarClick = { viewModel.toggleStar(story) },
                                onPinClick = { viewModel.togglePin(story) },
                                onExportClick = {
                                    PdfGenerator.generateStoryPdf(context, story) { file ->
                                        if (file != null) {
                                            val uri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file
                                            )
                                            val sendIntent: Intent = Intent().apply {
                                                action = Intent.ACTION_SEND
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                type = "application/pdf"
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(sendIntent, "Share Journal PDF"))
                                        }
                                    }
                                },
                                onClick = { onStoryClick(story) },
                                onMediaClick = { path, _ -> 
                                    val allMedia = story.images.map { it to false } + story.videos.map { it to true }
                                    val index = allMedia.indexOfFirst { it.first == path }
                                    fullScreenMediaInfo = allMedia to index
                                },
                                onLocationClick = { locationToViewInApp = it }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showQrScanner) {
        QrScannerScreen(
            onResult = { result ->
                scannedContent = result
                showQrScanner = false
            },
            onDismiss = { showQrScanner = false }
        )
    }

    fullScreenMediaInfo?.let { (allMedia, index) ->
        MediaFullScreenPreview(
            allMedia = allMedia,
            initialIndex = index,
            onDismiss = { fullScreenMediaInfo = null }
        )
    }

    if (storyToDelete != null) {
        AlertDialog(
            onDismissRequest = { storyToDelete = null },
            title = { Text("Delete Story") },
            text = { Text("Are you sure you want to delete '${storyToDelete?.title?.ifEmpty { "Untitled" } ?: "Untitled"}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        storyToDelete?.let { viewModel.deleteStory(it) }
                        storyToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { storyToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showReminderDialog) {
        AlertDialog(
            onDismissRequest = { showReminderDialog = false; reminderToEdit = null },
            title = { Text(if (reminderToEdit == null) "Add Reminder" else "Edit Reminder") },
            text = {
                Column(modifier = Modifier.imePadding()) {
                    OutlinedTextField(
                        value = reminderTitle,
                        onValueChange = { reminderTitle = it },
                        label = { Text("Reminder Title") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = reminderTitle.isBlank(),
                        supportingText = {
                            if (reminderTitle.isBlank()) {
                                Text("Title cannot be empty")
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Time (HH:mm)")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        var hourStr by remember { mutableStateOf(reminderTime.hour.toString().padStart(2, '0')) }
                        var minuteStr by remember { mutableStateOf(reminderTime.minute.toString().padStart(2, '0')) }
                        
                        OutlinedTextField(
                            value = hourStr,
                            onValueChange = { 
                                if (it.length <= 2 && it.all { c -> c.isDigit() }) {
                                    hourStr = it
                                    it.toIntOrNull()?.let { h -> if (h < 24) reminderTime = LocalTime.of(h, reminderTime.minute) }
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
                                    it.toIntOrNull()?.let { m -> if (m < 60) reminderTime = LocalTime.of(reminderTime.hour, m) }
                                }
                            },
                            modifier = Modifier.width(60.dp),
                            singleLine = true
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { isAlarm = !isAlarm }
                    ) {
                        Checkbox(checked = isAlarm, onCheckedChange = { isAlarm = it })
                        Text("Set as Alarm (louder sound)")
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
                                            selectedDays = if (isSelected) selectedDays - day else selectedDays + day
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = day.name.take(1),
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
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
                Button(
                    onClick = {
                        val date = selectedDate ?: LocalDate.now()
                        val combined = date.atTime(reminderTime)
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                        
                        val daysStr = selectedDays.joinToString(",") { it.name }
                        
                        if (reminderToEdit != null) {
                            viewModel.updateReminder(context, reminderToEdit!!.copy(
                                title = reminderTitle, 
                                timestamp = combined, 
                                isAlarm = isAlarm,
                                repeatType = repeatType,
                                repeatDays = daysStr
                            ))
                        } else {
                            viewModel.addReminder(context, reminderTitle, combined, isAlarm, repeatType, daysStr)
                        }
                        
                        showReminderDialog = false
                        reminderToEdit = null
                        reminderTitle = ""
                    },
                    enabled = reminderTitle.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReminderDialog = false; reminderToEdit = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (reminderToDelete != null) {
        AlertDialog(
            onDismissRequest = { reminderToDelete = null },
            title = { Text("Delete Reminder") },
            text = { Text("Are you sure you want to delete \"${reminderToDelete?.title}\"?") },
            confirmButton = {
                Button(
                    onClick = {
                        reminderToDelete?.let { viewModel.deleteReminder(it) }
                        reminderToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { reminderToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showQrOptions) {
        ModalBottomSheet(
            onDismissRequest = { showQrOptions = false },
            sheetState = bottomSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "QR Actions",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(120.dp)
                            .clickable {
                                showQrOptions = false
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                    showQrScanner = true
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Scanner", fontWeight = FontWeight.SemiBold)
                        }
                    }


                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(120.dp)
                            .clickable {
                                showQrOptions = false
                                showQrGenerator = true
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Generator", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    if (showQrGenerator) {
        ModernQrGeneratorDialog(
            stories = stories,
            initialText = qrContentToGenerate,
            onDismiss = { showQrGenerator = false }
        )
    }

    if (scannedContent != null) {
        val matchingStory = stories.find { it.id == scannedContent }
        val isUrl = Patterns.WEB_URL.matcher(scannedContent!!).matches()

        AlertDialog(
            onDismissRequest = { scannedContent = null },
            title = {
                Text(
                    when {
                        matchingStory != null -> "Story Found"
                        isUrl -> "Link Found"
                        else -> "QR Scanned"
                    }
                )
            },
            text = {
                Column {
                    if (matchingStory != null) {
                        Text("Scanned code matches story: ${matchingStory.title}")
                    } else if (isUrl) {
                        Text("Scanned link:")
                        Text(
                            scannedContent!!,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline
                            ),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                try {
                                    val url = if (!scannedContent!!.startsWith("http://") && !scannedContent!!.startsWith("https://")) {
                                        "https://$scannedContent"
                                    } else {
                                        scannedContent!!
                                    }
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    } else {
                        Text("Content:")
                        Text(scannedContent!!, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (matchingStory != null) {
                        onStoryClick(matchingStory)
                    } else if (isUrl) {
                        try {
                            val url = if (!scannedContent!!.startsWith("http://") && !scannedContent!!.startsWith("https://")) {
                                "https://$scannedContent"
                            } else {
                                scannedContent!!
                            }
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
                        }
                    }
                    scannedContent = null
                }) {
                    Text(
                        when {
                            matchingStory != null -> "Open Story"
                            isUrl -> "Open Link"
                            else -> "OK"
                        }
                    )
                }
            },
            dismissButton = if (matchingStory != null || isUrl) {
                {
                    TextButton(onClick = { scannedContent = null }) {
                        Text("Cancel")
                    }
                }
            } else null
        )
    }

    if (!locationToViewInApp.isNullOrBlank()) {
        InAppLocationDialog(
            location = locationToViewInApp!!,
            onDismiss = { locationToViewInApp = null }
        )
    }

    if (showRssFeed) {
        RssFeedScreen(
            onDismiss = { showRssFeed = false }
        )
    }

    if (showCalculator) {
        ScientificCalculatorScreen(
            onDismiss = { showCalculator = false }
        )
    }

    if (showTranslator) {
        OfflineTranslatorScreen(
            onDismiss = { showTranslator = false }
        )
    }
}

@Composable
fun StoryItem(
    story: Story,
    onStarClick: () -> Unit,
    onPinClick: () -> Unit,
    onExportClick: () -> Unit,
    onClick: () -> Unit,
    onMediaClick: (String, Boolean) -> Unit,
    onLocationClick: ((String) -> Unit)? = null,
) {
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val dateFormatter = remember(locale) { SimpleDateFormat("MMM d, yyyy", locale) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (story.isPinned) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (story.mood != null) {
                        Text(text = story.mood, style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (story.isPinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = story.title.ifEmpty { "Untitled" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = dateFormatter.format(Date(story.timestamp)),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            if (!story.location.isNullOrBlank() || story.tags.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!story.location.isNullOrBlank()) {
                        Surface(
                            onClick = { onLocationClick?.invoke(story.location) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = story.location,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Map,
                                    contentDescription = "In-App Map",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    }
                    story.tags.forEach { tag ->
                        Text(
                            text = "#$tag",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            }

            if (story.images.isNotEmpty() || story.videos.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    itemsIndexed(story.images) { _, imagePath ->
                        SecureTimelineThumbnail(
                            path = imagePath,
                            isVideo = false,
                            onClick = { onMediaClick(imagePath, false) }
                        )
                    }
                    itemsIndexed(story.videos) { _, videoPath ->
                        SecureTimelineThumbnail(
                            path = videoPath,
                            isVideo = true,
                            onClick = { onMediaClick(videoPath, true) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = MarkdownUtils.parseMarkdown(story.content),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Words: ${story.wordCount} | ${story.pages.size} pgs",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row {
                    IconButton(onClick = onExportClick) {
                        Icon(Icons.Default.Share, contentDescription = "Export")
                    }
                    IconButton(onClick = onPinClick) {
                        Icon(
                            imageVector = if (story.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Pin",
                            tint = if (story.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onStarClick) {
                        Icon(
                            imageVector = if (story.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Star",
                            tint = if (story.isStarred) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SecureTimelineThumbnail(
    path: String,
    isVideo: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    LaunchedEffect(path) {
        val decryptedFile = withContext(Dispatchers.IO) {
            FileEncryptionManager.decryptToTempFile(context, File(path))
        }
        if (decryptedFile != null) {
            val loadedBitmap = if (isVideo) {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(decryptedFile.absolutePath)
                val frame = retriever.getFrameAtTime(1000000)
                retriever.release()
                frame
            } else {
                BitmapFactory.decodeFile(decryptedFile.absolutePath)
            }
            bitmap = loadedBitmap
            decryptedFile.delete()
        }
    }

    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Gray.copy(alpha = 0.1f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (isVideo) {
                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(24.dp)
                )
            }
        } else {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernQrGeneratorDialog(
    stories: List<Story>,
    initialText: String = "",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var inputContent by remember { mutableStateOf(initialText) }
    var selectedColorArgb by remember { mutableIntStateOf(android.graphics.Color.parseColor("#1E1E2C")) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val colorOptions = listOf(
        "Indigo" to android.graphics.Color.parseColor("#1E1E2C"),
        "Navy" to android.graphics.Color.parseColor("#0D47A1"),
        "Purple" to android.graphics.Color.parseColor("#4A148C"),
        "Emerald" to android.graphics.Color.parseColor("#004D40"),
        "Crimson" to android.graphics.Color.parseColor("#880E4F"),
        "Obsidian" to android.graphics.Color.BLACK
    )

    // Real-time live QR code generation as user types or picks colors
    LaunchedEffect(inputContent, selectedColorArgb) {
        if (inputContent.isNotBlank()) {
            withContext(Dispatchers.IO) {
                qrBitmap = QrGeneratorManager.generateQrCode(inputContent, size = 640, darkColor = selectedColorArgb)
            }
        } else {
            qrBitmap = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "QR Code Generator",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Live real-time code creation",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        if (inputContent.isNotEmpty()) {
                            TextButton(onClick = { inputContent = "" }) {
                                Text("Clear")
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                )

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Quick Templates Row
                    if (stories.isNotEmpty()) {
                        val latestStory = stories.firstOrNull()
                        if (latestStory != null) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Quick Templates",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    item {
                                        AssistChip(
                                            onClick = { inputContent = latestStory.id },
                                            label = { Text("Latest Story ID", fontSize = 12.sp) },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.MenuBook,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        )
                                    }
                                    item {
                                        AssistChip(
                                            onClick = { inputContent = latestStory.title.ifEmpty { "My Story" } },
                                            label = { Text("Story Title", fontSize = 12.sp) }
                                        )
                                    }
                                    item {
                                        AssistChip(
                                            onClick = { inputContent = "Encrypted Journal Entry" },
                                            label = { Text("Encrypted Journal", fontSize = 12.sp) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Content Input Field
                    OutlinedTextField(
                        value = inputContent,
                        onValueChange = { inputContent = it },
                        label = { Text("Enter Text, Story ID, or Website Link") },
                        placeholder = { Text("Type text here to generate live QR code...") },
                        singleLine = false,
                        maxLines = 4,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (inputContent.isNotEmpty()) {
                                IconButton(onClick = { inputContent = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        }
                    )

                    // Color Palette Selector
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "QR Color Palette",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            colorOptions.forEach { (_, color) ->
                                val isSelected = selectedColorArgb == color
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(color))
                                        .border(
                                            width = if (isSelected) 3.5.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColorArgb = color }
                                )
                            }
                        }
                    }

                    // Live Generated QR Preview Card
                    if (qrBitmap != null) {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = Color(0xFFFAFAFC),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .size(240.dp)
                                .padding(top = 8.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(18.dp)
                            ) {
                                Image(
                                    bitmap = qrBitmap!!.asImageBitmap(),
                                    contentDescription = "Generated Real-Time QR Code",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        // Icon-Only Action Buttons Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Copy Text Button
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(inputContent))
                                    Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Content",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(20.dp))

                            // Save to Gallery Button
                            IconButton(
                                onClick = {
                                    QrGeneratorManager.saveQrToGallery(context, qrBitmap!!)
                                    Toast.makeText(context, "Saved to Gallery", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Save to Gallery",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(20.dp))

                            // Share QR Code Button
                            IconButton(
                                onClick = {
                                    QrGeneratorManager.shareQrCode(context, qrBitmap!!)
                                },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share QR Code",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.QrCode,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Type content above to generate live QR code",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
