package com.encryptedjournal.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.view.SoundEffectConstants
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.MediaController
import android.widget.Toast
import android.net.Uri
import android.content.Intent
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.encryptedjournal.R
import com.encryptedjournal.data.Story
import com.encryptedjournal.data.FileEncryptionManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import kotlin.math.absoluteValue
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.Instant
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.ui.text.style.TextDecoration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryDetailScreen(
    story: Story,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    onUpdateStory: ((Story) -> Unit)? = null
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val dateFormatter = remember(locale) { SimpleDateFormat("MMMM d, yyyy", locale) }
    val voicePlayer = remember { VoicePlayer() }
    var currentlyPlayingVoice by remember { mutableStateOf<String?>(null) }

    val pagerState = rememberPagerState(pageCount = { story.pages.size })
    val allMedia = remember(story) { story.images.map { it to false } + story.videos.map { it to true } }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current

    var fullScreenMediaIndex by remember { mutableStateOf<Int?>(null) }
    var showFullScreenPages by remember { mutableStateOf(false) }
    var showInAppMapDialog by remember { mutableStateOf(false) }
    
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
        // Play system sound immediately for best response
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
                Log.e("StoryDetail", "Error playing high-quality sound", e)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voicePlayer.stop()
            pageTurnPlayer.release()
            FileEncryptionManager.clearDecryptedCache(context)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(story.title.ifEmpty { "Untitled" }, style = MaterialTheme.typography.titleMedium)
                        Text(dateFormatter.format(Date(story.timestamp)), style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { 
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
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share PDF")
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Media and metadata header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!story.location.isNullOrBlank()) {
                    InAppMapCard(
                        location = story.location,
                        onClickExpand = { showInAppMapDialog = true }
                    )
                }

                if (story.mood != null || story.tags.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (story.mood != null) {
                            Text(text = story.mood, style = MaterialTheme.typography.headlineSmall)
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        if (story.tags.isNotEmpty()) {
                            FlowRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                story.tags.forEach { tag ->
                                    SuggestionChip(
                                        onClick = { },
                                        label = { Text("#$tag") }
                                    )
                                }
                            }
                        }
                    }
                }



                if (story.images.isNotEmpty() || story.videos.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(story.images) { index, imagePath ->
                            SecureMediaThumbnail(
                                path = imagePath,
                                isVideo = false,
                                onClick = { fullScreenMediaIndex = index }
                            )
                        }
                        itemsIndexed(story.videos) { index, videoPath ->
                            SecureMediaThumbnail(
                                path = videoPath,
                                isVideo = true,
                                onClick = { fullScreenMediaIndex = story.images.size + index }
                            )
                        }
                    }
                }

                if (story.voiceNotes.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(story.voiceNotes) { index, path ->
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
                                            val decFile = withContext(Dispatchers.IO) {
                                                FileEncryptionManager.decryptToTempFile(context, File(path))
                                            }
                                            if (decFile != null) {
                                                voicePlayer.play(decFile) {
                                                    currentlyPlayingVoice = null
                                                    decFile.delete()
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Real Page Pager
            // Trigger sound when the target page changes during a scroll, 
            // but only if it's a valid page transition (not a boundary bump)
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
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 32.dp),
                pageSpacing = 0.dp, // Reduced spacing for overlapping feel
                beyondViewportPageCount = 1
            ) { pageIndex ->
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 16.dp)
                        .graphicsLayer {
                            val pageOffset = ((pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction)
                            
                            // Rotation effect from the spine
                            val isLeftPage = pageOffset > 0
                            transformOrigin = TransformOrigin(if (isLeftPage) 1f else 0f, 0.5f)
                            rotationY = -25f * pageOffset.coerceIn(-1f, 1f)
                            
                            // Scale effect
                            val scale = lerp(0.9f, 1f, 1f - pageOffset.absoluteValue.coerceIn(0f, 1f))
                            scaleX = scale
                            scaleY = scale
                            
                            // Shadow/Alpha effect
                            alpha = lerp(0.6f, 1f, 1f - pageOffset.absoluteValue.coerceIn(0f, 1f))
                            
                            cameraDistance = 12 * density
                        }
                        .clickable { showFullScreenPages = true },
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFF9E3) // Paper color
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "Page ${pageIndex + 1} of ${story.pages.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            modifier = Modifier.align(Alignment.End)
                        )

                        val currentPageChecklist = story.pageChecklists[pageIndex] ?: emptyList()
                        if (currentPageChecklist.isNotEmpty()) {
                            var detailChecklistItems by remember(story, pageIndex) { mutableStateOf(currentPageChecklist) }

                            Spacer(Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Transparent,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    detailChecklistItems.forEachIndexed { index, item ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = item.isChecked,
                                                onCheckedChange = { checked ->
                                                    val updated = detailChecklistItems.toMutableList()
                                                    updated[index] = item.copy(isChecked = checked)
                                                    detailChecklistItems = updated
                                                    val fullMap = story.pageChecklists.toMutableMap()
                                                    fullMap[pageIndex] = updated
                                                    val updatedJson = Story.encodePageChecklists(fullMap)
                                                    onUpdateStory?.invoke(story.copy(checklistJson = updatedJson))
                                                }
                                            )
                                            Text(
                                                text = item.text,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (item.isChecked) Color.Gray else Color(story.textColor),
                                                textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        val pageFontSize = story.getFontSizeForPage(pageIndex)
                        val pageTextColor = story.getTextColorForPage(pageIndex)

                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = MarkdownUtils.parseMarkdown(story.pages[pageIndex]),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = pageFontSize.sp,
                                lineHeight = (pageFontSize * 1.5).sp,
                                color = Color(pageTextColor)
                            )
                        )
                    }
                }
            }
        }
    }

    fullScreenMediaIndex?.let { index ->
        MediaFullScreenPreview(
            allMedia = allMedia,
            initialIndex = index,
            onDismiss = { fullScreenMediaIndex = null }
        )
    }

    if (showFullScreenPages) {
        Dialog(
            onDismissRequest = { showFullScreenPages = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val offsetY = remember { Animatable(0f) }
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, offsetY.value.roundToInt()) }
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            val newOffset = offsetY.value + delta
                            if (newOffset > 0) {
                                scope.launch { offsetY.snapTo(newOffset) }
                            }
                        },
                        onDragStopped = { velocity ->
                            if (offsetY.value > 300f || velocity > 1000f) {
                                showFullScreenPages = false
                            } else {
                                scope.launch { offsetY.animateTo(0f) }
                            }
                        }
                    ),
                color = Color.Black.copy(alpha = (0.95f - (offsetY.value / 1000f)).coerceIn(0f, 0.95f))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val fullPagerState = rememberPagerState(
                        initialPage = pagerState.currentPage,
                        pageCount = { story.pages.size }
                    )
                    var lastTriggeredFullTarget by remember { mutableIntStateOf(-1) }
                    LaunchedEffect(fullPagerState) {
                        snapshotFlow { fullPagerState.targetPage }.collect { target ->
                            if (fullPagerState.isScrollInProgress && target != fullPagerState.currentPage && target != lastTriggeredFullTarget) {
                                playPageTurn()
                                lastTriggeredFullTarget = target
                                // Sync back to main pager
                                pagerState.scrollToPage(target)
                            } else if (!fullPagerState.isScrollInProgress) {
                                lastTriggeredFullTarget = -1
                            }
                        }
                    }
                    HorizontalPager(
                        state = fullPagerState,
                        modifier = Modifier.fillMaxSize(),
                        pageSpacing = 0.dp,
                        beyondViewportPageCount = 1
                    ) { pageIndex ->
                        Card(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .graphicsLayer {
                                    val pageOffset = ((fullPagerState.currentPage - pageIndex) + fullPagerState.currentPageOffsetFraction)
                                    val isLeftPage = pageOffset > 0
                                    transformOrigin = TransformOrigin(if (isLeftPage) 1f else 0f, 0.5f)
                                    rotationY = -25f * pageOffset.coerceIn(-1f, 1f)
                                    val scale = lerp(0.9f, 1f, 1f - pageOffset.absoluteValue.coerceIn(0f, 1f))
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = lerp(0.6f, 1f, 1f - pageOffset.absoluteValue.coerceIn(0f, 1f))
                                    cameraDistance = 12 * density
                                }
                                .clickable { showFullScreenPages = false },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9E3)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = "Page ${pageIndex + 1} of ${story.pages.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray,
                                    modifier = Modifier.align(Alignment.End)
                                )

                                val fullPageChecklist = story.pageChecklists[pageIndex] ?: emptyList()
                                if (fullPageChecklist.isNotEmpty()) {
                                    var detailChecklistItems by remember(story, pageIndex) { mutableStateOf(fullPageChecklist) }

                                    Spacer(Modifier.height(10.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.Transparent,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            detailChecklistItems.forEachIndexed { index, item ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Checkbox(
                                                        checked = item.isChecked,
                                                        onCheckedChange = { checked ->
                                                            val updated = detailChecklistItems.toMutableList()
                                                            updated[index] = item.copy(isChecked = checked)
                                                            detailChecklistItems = updated
                                                            val fullMap = story.pageChecklists.toMutableMap()
                                                            fullMap[pageIndex] = updated
                                                            val updatedJson = Story.encodePageChecklists(fullMap)
                                                            onUpdateStory?.invoke(story.copy(checklistJson = updatedJson))
                                                        }
                                                    )
                                                    Text(
                                                        text = item.text,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = if (item.isChecked) Color.Gray else Color(story.textColor),
                                                        textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                val pageFontSize = story.getFontSizeForPage(pageIndex)
                                val pageTextColor = story.getTextColorForPage(pageIndex)

                                Spacer(Modifier.height(24.dp))
                                Text(
                                    text = MarkdownUtils.parseMarkdown(story.pages[pageIndex]),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = (pageFontSize + 2).sp,
                                        lineHeight = ((pageFontSize + 2) * 1.6).sp,
                                        color = Color(pageTextColor)
                                    )
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { showFullScreenPages = false },
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).padding(top = 32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }
    }

    if (showInAppMapDialog && !story.location.isNullOrBlank()) {
        InAppLocationDialog(
            location = story.location,
            onDismiss = { showInAppMapDialog = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        content = { content() }
    )
}

@Composable
fun SecureMediaThumbnail(
    path: String,
    isVideo: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    
    LaunchedEffect(path) {
        val decryptedFile = withContext(Dispatchers.IO) {
            // Split path if it's a dual video (legacy or merged with | separator)
            val firstPath = path.split("|")[0]
            FileEncryptionManager.decryptToTempFile(context, File(firstPath))
        }
        if (decryptedFile != null) {
            val loadedBitmap = if (isVideo) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(decryptedFile.absolutePath)
                    retriever.getFrameAtTime(1000000)
                } catch (ignored: Exception) {
                    null
                } finally {
                    retriever.release()
                }
            } else {
                BitmapFactory.decodeFile(decryptedFile.absolutePath)
            }
            bitmap = loadedBitmap
            // Note: Cleanup is handled at the screen level now
        }
    }

    Box(
        modifier = Modifier
            .size(120.dp, 80.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Gray.copy(alpha = 0.2f))
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
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        } else {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}
