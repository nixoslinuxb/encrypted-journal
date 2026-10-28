package com.encryptedjournal.ui

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.MediaController
import android.widget.Toast
import android.util.Log
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.RectF
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.encryptedjournal.data.FileEncryptionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayList
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun MediaFullScreenPreview(
    allMedia: List<Pair<String, Boolean>>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isExporting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { allMedia.size })
    var isAnyVideoPlaying by remember { mutableStateOf(false) }
    var areIconsVisible by remember { mutableStateOf(true) }
    
    // For swipe down to dismiss
    val offsetY = remember { Animatable(0f) }

    DisposableEffect(Unit) {
        onDispose {
            FileEncryptionManager.clearDecryptedCache(context)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        isAnyVideoPlaying = false
    }

    Dialog(
        onDismissRequest = { if (!isExporting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = (1f - (offsetY.value.absoluteValue / 1000f)).coerceIn(0f, 1f)))
                .offset { IntOffset(0, offsetY.value.roundToInt()) }
                .draggable(
                    orientation = Orientation.Vertical,
                    enabled = !isAnyVideoPlaying,
                    state = rememberDraggableState { delta ->
                        if (delta > 0 || offsetY.value > 0) {
                            scope.launch {
                                offsetY.snapTo(offsetY.value + delta)
                            }
                        }
                    },
                    onDragStopped = {
                        if (offsetY.value > 300f) {
                            onDismiss()
                        } else {
                            offsetY.animateTo(0f)
                        }
                    }
                )
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !isExporting && !isAnyVideoPlaying,
                pageSpacing = 16.dp
            ) { index ->
                val (mediaPath, isVideo) = allMedia[index]
                
                var decryptedMainFile by remember { mutableStateOf<File?>(null) }
                var decryptedSelfieFile by remember { mutableStateOf<File?>(null) }
                
                LaunchedEffect(mediaPath) {
                    withContext(Dispatchers.IO) {
                        val paths = mediaPath.split("|")
                        decryptedMainFile = FileEncryptionManager.decryptToTempFile(context, File(paths[0]))
                        if (paths.size > 1) {
                            decryptedSelfieFile = FileEncryptionManager.decryptToTempFile(context, File(paths[1]))
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    decryptedMainFile?.let { mainFile ->
                        if (isVideo) {
                            VideoPlayerWithControls(
                                mainFile = mainFile,
                                selfieFile = decryptedSelfieFile,
                                modifier = Modifier.fillMaxSize(),
                                showControls = areIconsVisible,
                                onToggleControls = { areIconsVisible = !areIconsVisible },
                                onPlayingStateChanged = { isAnyVideoPlaying = it }
                            )
                        } else {
                            AsyncImage(
                                model = mainFile,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { areIconsVisible = !areIconsVisible },
                                contentScale = ContentScale.Fit
                            )
                        }
                    } ?: run {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }

            if (isExporting) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(Modifier.height(16.dp))
                        Text("Merging Media...", color = Color.White)
                    }
                }
            }

            // Top Bar Controls
            AnimatedVisibility(
                visible = areIconsVisible && !isExporting,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val currentMedia = allMedia[pagerState.currentPage]
                
                suspend fun prepareMediaForExport(path: String, isVideo: Boolean): String? {
                    isExporting = true
                    return try {
                        val paths = path.split("|")
                        val decryptedFiles = paths.map { p ->
                            withContext(Dispatchers.IO) {
                                FileEncryptionManager.decryptToTempFile(context, File(p))
                            }
                        }.filterNotNull()

                        if (decryptedFiles.isEmpty()) return null

                        if (isVideo && decryptedFiles.size > 1) {
                            // Merge dual video
                            val mergedFile = File(context.cacheDir, "Export_Merged_${System.currentTimeMillis()}.mp4")
                            DualVideoMerger.mergeVideos(
                                context = context,
                                mainPath = decryptedFiles[0].absolutePath,
                                frontPath = decryptedFiles[1].absolutePath,
                                outputPath = mergedFile.absolutePath
                            )
                            // Clean up decrypted segments
                            decryptedFiles.forEach { it.delete() }
                            mergedFile.absolutePath
                        } else {
                            // Single file or photo
                            decryptedFiles[0].absolutePath
                        }
                    } catch (e: Exception) {
                        Log.e("MediaPreview", "Export preparation failed", e)
                        null
                    } finally {
                        isExporting = false
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .padding(top = 32.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                val (path, isVid) = currentMedia
                                val exportPath = prepareMediaForExport(path, isVid)
                                if (exportPath != null) {
                                    saveToGallery(context, exportPath, isVid)
                                    // Delete the temporary export file if it was created in cache
                                    if (exportPath.contains("Export_Merged") || exportPath.contains("dec_")) {
                                        File(exportPath).delete()
                                    }
                                    Toast.makeText(context, "Saved to Gallery", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Save", tint = Color.White)
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            scope.launch {
                                val (path, isVid) = currentMedia
                                val exportPath = prepareMediaForExport(path, isVid)
                                if (exportPath != null) {
                                    shareMedia(context, listOf(exportPath), isVid)
                                    // Note: Share usually happens in background, 
                                    // we might need to delete this file later or keep it in cache
                                } else {
                                    Toast.makeText(context, "Share failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun VideoPlayerWithControls(
    mainFile: File,
    selfieFile: File?,
    modifier: Modifier = Modifier,
    showControls: Boolean,
    onToggleControls: () -> Unit,
    onPlayingStateChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val scope = rememberCoroutineScope()

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(Uri.fromFile(mainFile)))
            prepare()
        }
    }

    val selfiePlayer = remember(selfieFile) {
        selfiePlayerWithFile(context, selfieFile)
    }

    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var brightnessLevel by remember { 
        val current = activity?.window?.attributes?.screenBrightness ?: -1f
        mutableFloatStateOf(if (current < 0) 0.5f else current)
    }
    var showVolumeIndicator by remember { mutableStateOf(false) }
    var volumeLevel by remember { 
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat().coerceAtLeast(1f)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
        mutableFloatStateOf(current / max)
    }
    var isMuted by remember { mutableStateOf(false) }
    var previousVolume by remember { mutableFloatStateOf(1f) }

    // Sync state with player
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                isPlaying = isPlayingNow
                onPlayingStateChanged(isPlayingNow)
                if (isPlayingNow) {
                    duration = player.duration.coerceAtLeast(0L)
                }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    duration = player.duration.coerceAtLeast(0L)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
            selfiePlayer?.release()
        }
    }

    // Progress timer
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = player.currentPosition
            delay(500L)
        }
    }

    Box(modifier = modifier
        .pointerInput(isPlaying) {
            detectTapGestures(
                onDoubleTap = { offset ->
                    if (isPlaying && offset.x < size.width / 2) {
                        if (isMuted) {
                            player.volume = previousVolume
                            isMuted = false
                        } else {
                            previousVolume = player.volume
                            player.volume = 0f
                            isMuted = true
                        }
                        volumeLevel = player.volume
                        showVolumeIndicator = true
                        scope.launch {
                            delay(1000.milliseconds)
                            showVolumeIndicator = false
                        }
                    }
                },
                onTap = {
                    onToggleControls()
                }
            )
        }
        .pointerInput(isPlaying) {
            if (isPlaying) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        val isRightSide = change.position.x > size.width / 2
                        if (isRightSide) {
                            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val delta = -(dragAmount / size.height) * maxVolume * 3
                            val newVolume = (currentVolume + delta).coerceIn(0f, maxVolume.toFloat())
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume.toInt(), 0)
                            
                            if (isMuted && newVolume > 0) {
                                isMuted = false
                            }
                            
                            player.volume = 1f
                            volumeLevel = newVolume / maxVolume
                            showVolumeIndicator = true
                            showBrightnessIndicator = false
                        } else {
                            activity?.window?.let { window ->
                                val params = window.attributes
                                val currentBrightness = if (params.screenBrightness < 0) 0.5f else params.screenBrightness
                                val delta = -(dragAmount / size.height) * 4
                                val newBrightness = (currentBrightness + delta).coerceIn(0.01f, 1f)
                                params.screenBrightness = newBrightness
                                window.attributes = params
                                brightnessLevel = newBrightness
                                showBrightnessIndicator = true
                                showVolumeIndicator = false
                            }
                        }
                    },
                    onDragEnd = {
                        scope.launch {
                            delay(1000.milliseconds)
                            showBrightnessIndicator = false
                            showVolumeIndicator = false
                        }
                    }
                )
            }
        }
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { /* Handled by detectTapGestures */ }) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        selfiePlayer?.let { sp ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 80.dp, end = 16.dp)
                    .size(90.dp, 120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            this.player = sp
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Sync selfie player with main
            LaunchedEffect(isPlaying) {
                if (isPlaying) sp.play() else sp.pause()
            }
            LaunchedEffect(currentPosition) {
                // Sync positions if they drift
                if ((sp.currentPosition - player.currentPosition).absoluteValue > 1000) {
                    sp.seekTo(player.currentPosition)
                }
            }
        }

        // Overlay Controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Center Play/Pause
                IconButton(
                    onClick = { if (isPlaying) player.pause() else player.play() },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(80.dp)
                        .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }

                // Bottom Control Bar
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 64.dp)
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = formatTime(currentPosition),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )

                        Slider(
                            value = currentPosition.toFloat(),
                            onValueChange = { 
                                player.seekTo(it.toLong())
                                currentPosition = it.toLong()
                            },
                            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )

                        Text(
                            text = formatTime(duration),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }

        // Gesture Indicators
        GestureIndicator(
            visible = showVolumeIndicator,
            icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            value = if (isMuted) 0f else volumeLevel,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp)
        )

        GestureIndicator(
            visible = showBrightnessIndicator,
            icon = Icons.Default.BrightnessLow,
            value = brightnessLevel,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 32.dp)
        )
    }
}

@Composable
fun GestureIndicator(
    visible: Boolean,
    icon: ImageVector,
    value: Float,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(vertical = 16.dp)
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(100.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.3f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(value)
                        .align(Alignment.BottomCenter)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

fun selfiePlayerWithFile(context: Context, selfieFile: File?): ExoPlayer? {
    return selfieFile?.let {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            setMediaItem(MediaItem.fromUri(Uri.fromFile(it)))
            prepare()
        }
    }
}

private fun saveToGallery(context: Context, path: String, isVideo: Boolean) {
    val file = File(path)
    if (!file.exists()) return

    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "Journal_${System.currentTimeMillis()}")
        put(MediaStore.MediaColumns.MIME_TYPE, if (isVideo) "video/mp4" else "image/jpeg")
        put(MediaStore.MediaColumns.RELATIVE_PATH, if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }

    val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val uri = context.contentResolver.insert(collection, values)

    uri?.let {
        try {
            context.contentResolver.openOutputStream(it)?.use { output ->
                file.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(it, values, null, null)
        } catch (e: Exception) {
            Log.e("MediaPreview", "Failed to save to gallery", e)
        }
    }
}

private fun shareMedia(context: Context, paths: List<String>, isVideo: Boolean) {
    val uris = ArrayList<Uri>()
    paths.forEach { path ->
        val file = File(path)
        if (file.exists()) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            uris.add(uri)
        }
    }

    if (uris.isEmpty()) return

    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uris[0])
            type = if (isVideo) "video/mp4" else "image/jpeg"
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            type = if (isVideo) "video/mp4" else "image/jpeg"
        }
    }
    
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Share Media"))
}
