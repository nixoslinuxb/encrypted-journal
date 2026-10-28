package com.encryptedjournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.encryptedjournal.data.Story
import com.encryptedjournal.data.Reminder
import com.encryptedjournal.R
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.util.Log
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    stories: List<Story>,
    reminders: List<Reminder>,
    onAddStoryForDate: (LocalDate) -> Unit,
    onAddReminder: (String, Long, Boolean, String, String) -> Unit,
    onUpdateReminder: (Reminder) -> Unit,
    onDeleteReminder: (Reminder) -> Unit,
    onStoryClick: (Story) -> Unit,
    onSeedHolidays: () -> Unit,
    onBack: () -> Unit
) {
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var viewMode by remember { mutableStateOf(CalendarViewMode.DAYS) }
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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
                // Fallback if SoundPool not ready
                try {
                    MediaPlayer.create(context, R.raw.page_flipc)?.apply {
                        setOnCompletionListener { it.release() }
                        start()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        isFirstLoad = false
    }

    var showReminderDialog by remember { mutableStateOf(false) }
    var reminderToEdit by remember { mutableStateOf<Reminder?>(null) }
    var reminderToDelete by remember { mutableStateOf<Reminder?>(null) }
    var reminderTitle by remember { mutableStateOf("") }
    var reminderTime by remember { mutableStateOf(LocalTime.now()) }
    var isAlarm by remember { mutableStateOf(false) }
    var repeatType by remember { mutableStateOf("ONCE") }
    var selectedDays by remember { mutableStateOf(setOf<DayOfWeek>()) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onSeedHolidays) {
                        Icon(Icons.Default.Flag, contentDescription = "Import Turkish Holidays")
                    }
                    IconButton(onClick = { showReminderDialog = true }) {
                        Icon(Icons.Default.Alarm, contentDescription = "Add Reminder")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAddStoryForDate(selectedDate) }) {
                Icon(Icons.Default.Add, contentDescription = "Add Story")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            CalendarHeader(
                currentMonth = currentMonth,
                locale = locale,
                viewMode = viewMode,
                onTodayClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(initialPage)
                    }
                    selectedDate = LocalDate.now()
                    viewMode = CalendarViewMode.DAYS
                },
                onTitleClick = {
                    viewMode = when (viewMode) {
                        CalendarViewMode.DAYS -> CalendarViewMode.MONTHS
                        CalendarViewMode.MONTHS -> CalendarViewMode.YEARS
                        CalendarViewMode.YEARS -> CalendarViewMode.DAYS
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            when (viewMode) {
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
                                        stop = 0.5f,
                                        fraction = pageOffset.absoluteValue
                                    )
                                }
                        ) {
                            CalendarGrid(
                                currentMonth = monthForPage,
                                selectedDate = if (monthForPage == YearMonth.from(selectedDate)) selectedDate else null,
                                storiesByDate = storiesByDate,
                                remindersByDate = remindersByDate,
                                onDateSelected = { selectedDate = it }
                            )
                        }
                    }
                }
                CalendarViewMode.MONTHS -> {
                    MonthPicker(
                        currentYear = currentMonth.year,
                        onMonthSelected = {
                            val targetMonth = YearMonth.of(currentMonth.year, it)
                            val monthsDiff = (targetMonth.year - anchorMonth.year) * 12 + (targetMonth.monthValue - anchorMonth.monthValue)
                            scope.launch {
                                pagerState.scrollToPage(monthsDiff)
                            }
                            viewMode = CalendarViewMode.DAYS
                        },
                        locale = locale
                    )
                }
                CalendarViewMode.YEARS -> {
                    YearPicker(
                        currentYear = currentMonth.year,
                        onYearSelected = {
                            val targetMonth = YearMonth.of(it, currentMonth.month)
                            val monthsDiff = (targetMonth.year - anchorMonth.year) * 12 + (targetMonth.monthValue - anchorMonth.monthValue)
                            scope.launch {
                                pagerState.scrollToPage(monthsDiff)
                            }
                            viewMode = CalendarViewMode.MONTHS
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Stories for ${selectedDate.dayOfMonth} ${selectedDate.month.getDisplayName(TextStyle.FULL, locale)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val selectedDateStories = storiesByDate[selectedDate] ?: emptyList()
            val selectedDateReminders = remindersByDate[selectedDate] ?: emptyList()
            val importantDay = getImportantDay(selectedDate)
            
            if (importantDay != null || selectedDateStories.isNotEmpty() || selectedDateReminders.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (importantDay != null) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Flag,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = importantDay.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = importantDay.note,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    if (selectedDateReminders.isNotEmpty()) {
                        item {
                            Text(
                                "Reminders",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(selectedDateReminders, key = { it.id }) { reminder ->
                            val time = remember(reminder.timestamp) {
                                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(reminder.timestamp))
                            }
                            SwipeableReminderItem(
                                reminder = reminder,
                                timeText = time,
                                onEdit = { reminderToEdit = it; showReminderDialog = true },
                                onDeleteRequest = { reminderToDelete = it }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }

                    if (selectedDateStories.isNotEmpty()) {
                        item {
                            Text(
                                "Stories",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(selectedDateStories) { story ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onStoryClick(story) },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (story.mood != null) {
                                        Text(story.mood, fontSize = 24.sp)
                                        Spacer(modifier = Modifier.width(12.dp))
                                    }
                                    Column {
                                        Text(
                                            text = story.title.ifEmpty { "Untitled" },
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${story.wordCount} words",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Nothing for this day.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
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
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    // Simple time picker placeholder - in a real app, use Material 3 TimePicker
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
                        val combined = selectedDate.atTime(reminderTime)
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                        
                        val daysStr = selectedDays.joinToString(",") { it.name }
                        
                        if (reminderToEdit != null) {
                            onUpdateReminder(reminderToEdit!!.copy(
                                title = reminderTitle, 
                                timestamp = combined, 
                                isAlarm = isAlarm,
                                repeatType = repeatType,
                                repeatDays = daysStr
                            ))
                        } else {
                            onAddReminder(reminderTitle, combined, isAlarm, repeatType, daysStr)
                        }
                        
                        showReminderDialog = false
                        reminderToEdit = null
                        reminderTitle = ""
                    }
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
                        reminderToDelete?.let { onDeleteReminder(it) }
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
}
