package com.encryptedjournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.encryptedjournal.data.Story
import com.encryptedjournal.data.Reminder
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.*

enum class CalendarViewMode { DAYS, MONTHS, YEARS }

data class ImportantDay(val name: String, val note: String)

fun getImportantDay(date: LocalDate): ImportantDay? {
    return when {
        date.monthValue == 1 && date.dayOfMonth == 1 ->
            ImportantDay("New Year's Day", "Celebration marking the beginning of the new year.")
        date.monthValue == 4 && date.dayOfMonth == 23 ->
            ImportantDay("National Sovereignty and Children's Day", "Commemorates the foundation of the Grand National Assembly of Turkey, dedicated to children.")
        date.monthValue == 5 && date.dayOfMonth == 19 ->
            ImportantDay("Commemoration of Atatürk, Youth and Sports Day", "Commemorates Atatürk's arrival in Samsun in 1919, marking the start of the War of Independence.")
        date.monthValue == 7 && date.dayOfMonth == 15 ->
            ImportantDay("Democracy and National Unity Day", "Honors national unity and democracy in Turkey.")
        date.monthValue == 8 && date.dayOfMonth == 30 ->
            ImportantDay("Victory Day", "Commemorates the decisive Turkish victory in the Battle of Dumlupınar in 1922.")
        date.monthValue == 10 && date.dayOfMonth == 29 ->
            ImportantDay("Republic Day", "Commemorates the official proclamation of the Republic of Turkey in 1923.")
        date.monthValue == 11 && date.dayOfMonth == 10 ->
            ImportantDay("Commemoration of Atatürk", "Commemorates Mustafa Kemal Atatürk, founder of the Republic of Turkey, who passed away in 1938.")
        else -> null
    }
}

fun isTurkishImportantDay(date: LocalDate): String? = getImportantDay(date)?.name

@Composable
fun CalendarHeader(
    currentMonth: YearMonth,
    locale: Locale,
    viewMode: CalendarViewMode,
    onTodayClick: () -> Unit,
    onTitleClick: () -> Unit
) {
    val isCurrentMonth = currentMonth == YearMonth.now()
    
    Box(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = when(viewMode) {
                CalendarViewMode.DAYS -> "${currentMonth.month.getDisplayName(TextStyle.FULL, locale)} ${currentMonth.year}"
                CalendarViewMode.MONTHS -> "${currentMonth.year}"
                CalendarViewMode.YEARS -> "${currentMonth.year - 6} - ${currentMonth.year + 5}"
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.Center)
                .clickable { onTitleClick() }
                .padding(8.dp)
        )
        
        if (!isCurrentMonth || viewMode != CalendarViewMode.DAYS) {
            IconButton(
                onClick = onTodayClick,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(
                    imageVector = Icons.Default.Today,
                    contentDescription = "Go to Today",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun CalendarGrid(
    currentMonth: YearMonth,
    selectedDate: LocalDate?,
    storiesByDate: Map<LocalDate, List<Story>>,
    remindersByDate: Map<LocalDate, List<Reminder>> = emptyMap(),
    onDateSelected: (LocalDate) -> Unit
) {
    val daysInMonth = currentMonth.lengthOfMonth()
    val firstDayOfMonth = (currentMonth.atDay(1).dayOfWeek.value - 1) // 0 for Monday, 6 for Sunday
    
    val days = (1..daysInMonth).toList()
    val paddingDays = (0 until firstDayOfMonth).toList()

    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.height(280.dp),
            userScrollEnabled = false
        ) {
            items(paddingDays) {
                Box(modifier = Modifier.aspectRatio(1f))
            }
            
            items(days) { day ->
                val date = currentMonth.atDay(day)
                val isSelected = date == selectedDate
                val isToday = date == LocalDate.now()
                val hasStories = storiesByDate.containsKey(date)
                val hasReminders = remindersByDate.containsKey(date)
                val holidayName = isTurkishImportantDay(date)
                
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .then(
                            if (isToday && !isSelected) {
                                Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            } else Modifier
                        )
                        .background(
                            when {
                                isSelected -> MaterialTheme.colorScheme.primary 
                                holidayName != null -> Color(0xFFFFEBEE) // Very light red for holidays
                                hasStories -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else -> Color.Transparent
                            }
                        )
                        .clickable { onDateSelected(date) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = day.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected || isToday || hasStories || hasReminders || holidayName != null) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary 
                                    else if (holidayName != null) Color(0xFFD32F2F) // Red for holidays
                                    else if (isToday) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            if (hasStories && !isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                            if (hasReminders && !isSelected) {
                                if (hasStories) Spacer(Modifier.width(2.dp))
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondary)
                                )
                            }
                            if (holidayName != null && !isSelected) {
                                if (hasStories || hasReminders) Spacer(Modifier.width(2.dp))
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFD32F2F))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MonthPicker(
    currentYear: Int,
    onMonthSelected: (Month) -> Unit,
    locale: Locale
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.height(280.dp),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(Month.entries) { month ->
            val isCurrentMonth = month == LocalDate.now().month && currentYear == LocalDate.now().year
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isCurrentMonth) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable { onMonthSelected(month) }
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = month.getDisplayName(TextStyle.SHORT, locale),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrentMonth) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun YearPicker(
    currentYear: Int,
    onYearSelected: (Int) -> Unit
) {
    val years = (currentYear - 6 .. currentYear + 5).toList()
    
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.height(280.dp),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(years) { year ->
            val isCurrentYear = year == LocalDate.now().year
            val isSelected = year == currentYear
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary 
                        else if (isCurrentYear) MaterialTheme.colorScheme.primaryContainer 
                        else Color.Transparent
                    )
                    .clickable { onYearSelected(year) }
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = year.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isSelected || isCurrentYear) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableReminderItem(
    reminder: Reminder,
    timeText: String,
    onEdit: (Reminder) -> Unit,
    onDeleteRequest: (Reminder) -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState()

    LaunchedEffect(dismissState.currentValue) {
        when (dismissState.currentValue) {
            SwipeToDismissBoxValue.EndToStart -> {
                // Swipe Left -> Edit
                onEdit(reminder)
                dismissState.snapTo(SwipeToDismissBoxValue.Settled)
            }
            SwipeToDismissBoxValue.StartToEnd -> {
                // Swipe Right -> Delete request (confirmation)
                onDeleteRequest(reminder)
                dismissState.snapTo(SwipeToDismissBoxValue.Settled)
            }
            SwipeToDismissBoxValue.Settled -> {}
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true, // Swipe Right -> Delete
        enableDismissFromEndToStart = true, // Swipe Left -> Edit
        modifier = modifier,
        backgroundContent = {
            val color = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.errorContainer
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.primaryContainer
                else -> Color.Transparent
            }
            val alignment = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CardDefaults.shape)
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Delete",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Edit",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Alarm,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "$timeText - ${reminder.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
