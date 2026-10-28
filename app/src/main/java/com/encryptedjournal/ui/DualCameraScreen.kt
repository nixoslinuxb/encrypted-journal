package com.encryptedjournal.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.absoluteValue
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

import android.widget.Toast
import kotlinx.coroutines.Dispatchers

enum class CamSelection { REAR, SELFIE, DUAL }
enum class RecordingState { IDLE, RECORDING, FINALIZING }

@Composable
fun DualCameraScreen(
    onMediaCaptured: (String, Boolean) -> Unit, // path, isVideo
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = ContextCompat.getMainExecutor(context)
    val scope = rememberCoroutineScope()

    var selectedCam by remember { mutableStateOf(CamSelection.REAR) }
    var isVideoMode by remember { mutableStateOf(false) }
    
    var rearRecording by remember { mutableStateOf<Recording?>(null) }
    var frontRecording by remember { mutableStateOf<Recording?>(null) }
    var recordingState by remember { mutableStateOf(RecordingState.IDLE) }
    var recordingDuration by remember { mutableLongStateOf(0L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isDualHardwareSupported by remember { mutableStateOf(false) }
    
    // Seamless switching state
    val recordingSegments = remember { mutableStateListOf<VideoSegment>() }
    var isSwitching by remember { mutableStateOf(false) }
    var pendingCamSwitch by remember { mutableStateOf<CamSelection?>(null) }
    var isWaitingForFinalization by remember { mutableStateOf(false) }
    val flipRotation = remember { Animatable(0f) }
    val switchAlpha = remember { Animatable(1f) }
    
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    
    // Helper to start the next segment safely
    fun performSafeSwitch(nextCam: CamSelection) {
        if (isSwitching || nextCam == selectedCam) return
        
        isSwitching = true
        pendingCamSwitch = nextCam
        
        // Stop current recording - the listener in startNewRecordingSegment will handle the next part
        if (rearRecording != null) {
            rearRecording?.stop()
            rearRecording = null
        } else if (frontRecording != null) {
            frontRecording?.stop()
            frontRecording = null
        } else {
            // Fallback if no active recording (shouldn't happen in RECORDING state)
            scope.launch {
                selectedCam = nextCam
                isSwitching = false
                pendingCamSwitch = null
            }
        }
    }
    
    val backPreviewView = remember { 
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val frontPreviewView = remember { 
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val frontOverlayPreviewView = remember { 
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    
    val backPreview = remember { Preview.Builder().build() }
    val backImageCapture = remember { ImageCapture.Builder().build() }
    val backRecorder = remember { Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityThan(Quality.HD))).build() }
    val backVideoCapture = remember { VideoCapture.Builder(backRecorder).setMirrorMode(MirrorMode.MIRROR_MODE_OFF).build() }

    val frontPreview = remember { Preview.Builder().build() }
    val frontImageCapture = remember { ImageCapture.Builder().build() }
    val frontRecorder = remember { Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.SD, FallbackStrategy.higherQualityOrLowerThan(Quality.SD))).build() }
    val frontVideoCapture = remember { VideoCapture.Builder(frontRecorder).setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY).build() }

    fun bindCameras(cameraProvider: ProcessCameraProvider) {
        try {
            cameraProvider.unbindAll()
            
            val concurrentInfos = cameraProvider.availableConcurrentCameraInfos
            val oldSupported = isDualHardwareSupported
            isDualHardwareSupported = concurrentInfos.any { set ->
                val facings = set.map { it.lensFacing }
                facings.contains(CameraSelector.LENS_FACING_BACK) && 
                facings.contains(CameraSelector.LENS_FACING_FRONT)
            }
            
            if (isDualHardwareSupported && !oldSupported) {
                Log.d("DualCamera", "Concurrent camera support detected")
            } else if (!isDualHardwareSupported) {
                Log.w("DualCamera", "Concurrent camera NOT supported on this device")
            }

            backPreview.surfaceProvider = backPreviewView.surfaceProvider
            // We use different PreviewViews for main vs overlay to avoid "already has a parent" crash
            frontPreview.surfaceProvider = if (selectedCam == CamSelection.DUAL) {
                frontOverlayPreviewView.surfaceProvider
            } else {
                frontPreviewView.surfaceProvider
            }

            when (selectedCam) {
                CamSelection.DUAL -> {
                    if (isDualHardwareSupported) {
                        val backConfig = ConcurrentCamera.SingleCameraConfig(
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            UseCaseGroup.Builder().addUseCase(backPreview).apply {
                                if (isVideoMode) addUseCase(backVideoCapture) else addUseCase(backImageCapture)
                            }.build(),
                            lifecycleOwner
                        )
                        val frontConfig = ConcurrentCamera.SingleCameraConfig(
                            CameraSelector.DEFAULT_FRONT_CAMERA,
                            UseCaseGroup.Builder().addUseCase(frontPreview).apply {
                                if (isVideoMode) addUseCase(frontVideoCapture) else addUseCase(frontImageCapture)
                            }.build(),
                            lifecycleOwner
                        )
                        cameraProvider.bindToLifecycle(listOf(backConfig, frontConfig))
                    } else {
                        errorMessage = "Dual Camera not supported"
                        selectedCam = CamSelection.REAR
                    }
                }
                CamSelection.REAR -> {
                    val captureUseCase = if (isVideoMode) backVideoCapture else backImageCapture
                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, backPreview, captureUseCase)
                }
                CamSelection.SELFIE -> {
                    val captureUseCase = if (isVideoMode) frontVideoCapture else frontImageCapture
                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, frontPreview, captureUseCase)
                }
            }
        } catch (e: Exception) {
            Log.e("DualCamera", "Binding failed", e)
            errorMessage = "Camera Error: ${e.message}"
            Toast.makeText(context, "Failed to initialize camera", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(recordingState) {
        if (recordingState == RecordingState.RECORDING) {
            val start = System.currentTimeMillis()
            while (recordingState == RecordingState.RECORDING) {
                recordingDuration = System.currentTimeMillis() - start
                delay(100.milliseconds)
            }
        } else {
            recordingDuration = 0L
        }
    }

    // Tracking segments
    val startedSegmentsCount = remember { AtomicInteger(0) }
    val finalizedSegmentsCount = remember { AtomicInteger(0) }

    fun startNewRecordingSegment() {
        val time = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(System.currentTimeMillis())
        
        val mainFile = File(context.filesDir, "Main_${selectedCam.name}_$time.mp4")
        val selfieFile = if (selectedCam == CamSelection.DUAL) File(context.filesDir, "Selfie_$time.mp4") else null
        
        val segment = VideoSegment(
            mainPath = mainFile.absolutePath,
            selfiePath = selfieFile?.absolutePath
        )
        recordingSegments.add(segment)

        val startTime = System.currentTimeMillis()

        // Track when this segment's recordings are done to set duration
        val completedCount = AtomicInteger(0)
        val expectedCount = if (selectedCam == CamSelection.DUAL) 2 else 1

        fun onRecordingFinalized() {
            if (completedCount.incrementAndGet() >= expectedCount) {
                segment.durationUs = (System.currentTimeMillis() - startTime) * 1000
                
                val currentFinalized = finalizedSegmentsCount.incrementAndGet()
                Log.d("DualCamera", "Segment finalized: $currentFinalized / ${startedSegmentsCount.get()}")

                if (isWaitingForFinalization && currentFinalized >= startedSegmentsCount.get()) {
                    isWaitingForFinalization = false
                    scope.launch {
                        val finalPath = File(context.filesDir, "Video_Merged_${System.currentTimeMillis()}.mp4").absolutePath
                        SequentialVideoMerger.mergeVideoSegments(
                            context = context,
                            segments = recordingSegments.toList(),
                            outputPath = finalPath,
                            onComplete = { path ->
                                recordingState = RecordingState.IDLE
                                recordingSegments.clear()
                                onMediaCaptured(path, true)
                            },
                            onError = { e ->
                                Log.e("DualCamera", "Merge failed", e)
                                recordingState = RecordingState.IDLE
                                recordingSegments.clear()
                                Toast.makeText(context, "Merge failed", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                pendingCamSwitch?.let { nextCam ->
                    scope.launch {
                        selectedCam = nextCam
                        pendingCamSwitch = null
                    }
                }
            }
        }

        startedSegmentsCount.incrementAndGet()

        // Start Main/Rear
        try {
            val mainPending = (if (selectedCam == CamSelection.SELFIE) frontVideoCapture else backVideoCapture)
                .output.prepareRecording(context, FileOutputOptions.Builder(mainFile).build())
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) mainPending.withAudioEnabled()
            
            val recording = mainPending.start(mainExecutor) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    if (event.hasError()) Log.e("DualCamera", "Main Record Error: ${event.error}")
                    onRecordingFinalized()
                }
            }
            if (selectedCam == CamSelection.SELFIE) frontRecording = recording else rearRecording = recording
        } catch (e: Exception) {
            Log.e("DualCamera", "Failed to start main recording", e)
        }

        // Start Selfie if in Dual mode
        if (selectedCam == CamSelection.DUAL && selfieFile != null) {
            try {
                // In dual mode, frontVideoCapture is also bound. 
                // We use backVideoCapture for "Main" and frontVideoCapture for "Selfie" overlay.
                val selfiePending = frontVideoCapture.output.prepareRecording(context, FileOutputOptions.Builder(selfieFile).build())
                // No audio for selfie track to avoid feedback/conflicts; main track has it.
                
                val recording = selfiePending.start(mainExecutor) { event ->
                    if (event is VideoRecordEvent.Finalize) {
                        if (event.hasError()) Log.e("DualCamera", "Selfie Record Error: ${event.error}")
                        onRecordingFinalized()
                    }
                }
                frontRecording = recording
            } catch (e: Exception) {
                Log.e("DualCamera", "Failed to start selfie recording", e)
            }
        }
    }

    LaunchedEffect(selectedCam, isVideoMode) {
        val cameraProvider = withContext(Dispatchers.IO) {
            cameraProviderFuture.get()
        }
        
        if (isSwitching && recordingState == RecordingState.RECORDING) {
            // Modern 3D Flip transition: 0 -> 90 with scale down and fade
            val anim1 = scope.launch { switchAlpha.animateTo(0.5f, tween(150)) }
            flipRotation.animateTo(
                targetValue = 90f,
                animationSpec = tween(durationMillis = 200)
            )
            anim1.join()
            
            bindCameras(cameraProvider)
            
            // Start next segment automatically
            startNewRecordingSegment()
            
            // Finish flip: -90 -> 0 with scale up and fade in
            flipRotation.snapTo(-90f)
            val anim2 = scope.launch { switchAlpha.animateTo(1f, tween(150)) }
            flipRotation.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 200)
            )
            anim2.join()
            
            isSwitching = false
        } else {
            bindCameras(cameraProvider)
        }
    }

    var offsetX by remember { mutableFloatStateOf(0f) }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
        .pointerInput(selectedCam, recordingState) {
            detectDragGestures(
                onDrag = { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                },
                onDragEnd = {
                    if (offsetX > 200) { // Swipe Right -> Rear
                        if (selectedCam != CamSelection.REAR) {
                            if (recordingState == RecordingState.RECORDING) performSafeSwitch(CamSelection.REAR)
                            else selectedCam = CamSelection.REAR
                        }
                    } else if (offsetX < -200) { // Swipe Left -> Selfie
                        if (selectedCam != CamSelection.SELFIE) {
                            if (recordingState == RecordingState.RECORDING) performSafeSwitch(CamSelection.SELFIE)
                            else selectedCam = CamSelection.SELFIE
                        }
                    }
                    offsetX = 0f
                }
            )
        }
        .pointerInput(selectedCam, recordingState) {
            detectTapGestures(
                onDoubleTap = {
                    if (recordingState == RecordingState.RECORDING) {
                        val next = if (selectedCam == CamSelection.REAR) CamSelection.SELFIE else CamSelection.REAR
                        performSafeSwitch(next)
                    } else if (recordingState == RecordingState.IDLE) {
                        selectedCam = if (selectedCam == CamSelection.REAR) CamSelection.SELFIE else CamSelection.REAR
                    }
                }
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationY = flipRotation.value
                    cameraDistance = 12 * density
                    
                    // Add a nice scale effect during the flip
                    val progress = flipRotation.value.absoluteValue / 90f
                    val scale = lerp(1f, 0.85f, progress)
                    scaleX = scale
                    scaleY = scale
                    
                    alpha = switchAlpha.value
                }
        ) {
            if (selectedCam == CamSelection.SELFIE) {
                AndroidView(factory = { frontPreviewView }, modifier = Modifier.fillMaxSize())
            } else {
                AndroidView(factory = { backPreviewView }, modifier = Modifier.fillMaxSize())
            }
        }

        // Top Navigation (Back)
        IconButton(
            onClick = {
                if (recordingState == RecordingState.IDLE) {
                    onDismiss()
                } else {
                    // Cancel recording if active
                    rearRecording?.stop()
                    frontRecording?.stop()
                    recordingSegments.forEach { 
                        File(it.mainPath).delete()
                        it.selfiePath?.let { p -> File(p).delete() }
                    }
                    recordingSegments.clear()
                    recordingState = RecordingState.IDLE
                    onDismiss()
                }
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp, 48.dp, 16.dp, 16.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit", tint = Color.White)
        }

        if (selectedCam == CamSelection.DUAL && isDualHardwareSupported) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp, 48.dp, 16.dp, 16.dp)
                    .size(120.dp, 160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, Color.White, RoundedCornerShape(12.dp))
            ) {
                AndroidView(factory = { frontOverlayPreviewView }, modifier = Modifier.fillMaxSize())
            }
        }

        // Mode and Camera selectors
        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(24.dp)).padding(4.dp)
            ) {
                CamSelection.entries.forEach { selection ->
                    val isSelected = selectedCam == selection
                    val isEnabled = selection != CamSelection.DUAL || isDualHardwareSupported
                    Button(
                        onClick = { 
                            if (recordingState == RecordingState.IDLE) {
                                if (isEnabled) {
                                    selectedCam = selection
                                } else if (selection == CamSelection.DUAL) {
                                    Toast.makeText(context, "Dual Camera not supported on this device", Toast.LENGTH_SHORT).show()
                                }
                            } else if (recordingState == RecordingState.RECORDING) {
                                if (isEnabled && selection != selectedCam) {
                                    performSafeSwitch(selection)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) Color.White else Color.Transparent,
                            contentColor = if (isSelected) Color.Black else (if (isEnabled) Color.White else Color.Gray)
                        ),
                        modifier = Modifier.height(40.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text(selection.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(24.dp)).padding(4.dp)
            ) {
                listOf(false to "Photo", true to "Video").forEach { (mode, label) ->
                    val isSelected = isVideoMode == mode
                    Button(
                        onClick = { if (recordingState == RecordingState.IDLE) isVideoMode = mode },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) Color.White else Color.Transparent,
                            contentColor = if (isSelected) Color.Black else Color.White
                        ),
                        modifier = Modifier.height(40.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            
            errorMessage?.let { msg ->
                Surface(modifier = Modifier.padding(top = 16.dp), color = Color.Red.copy(alpha = 0.7f), shape = RoundedCornerShape(8.dp)) {
                    Text(msg, color = Color.White, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Bottom Controls
        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Spacer(Modifier.size(48.dp))
                if (!isVideoMode) {
                    IconButton(
                        onClick = {
                            if (selectedCam == CamSelection.DUAL) {
                                Toast.makeText(context, "Capturing Dual Photo...", Toast.LENGTH_SHORT).show()
                                takeDualPhoto(context, backImageCapture, frontImageCapture, mainExecutor, onMediaCaptured)
                            } else {
                                takePhoto(context, if (selectedCam == CamSelection.SELFIE) frontImageCapture else backImageCapture, mainExecutor, selectedCam == CamSelection.SELFIE, onMediaCaptured)
                            }
                        },
                        modifier = Modifier.size(72.dp).background(Color.White, CircleShape).padding(4.dp)
                    ) { Box(Modifier.fillMaxSize().border(2.dp, Color.Black, CircleShape)) }
                } else {
                    IconButton(
                        onClick = {
                            if (recordingState == RecordingState.RECORDING) {
                                recordingState = RecordingState.FINALIZING
                                isWaitingForFinalization = true
                                rearRecording?.stop()
                                frontRecording?.stop()
                                rearRecording = null
                                frontRecording = null
                            } else if (recordingState == RecordingState.IDLE) {
                                recordingSegments.clear()
                                startedSegmentsCount.set(0)
                                finalizedSegmentsCount.set(0)
                                recordingState = RecordingState.RECORDING
                                startNewRecordingSegment()
                            }
                        },
                        modifier = Modifier.size(72.dp).border(4.dp, Color.White, CircleShape).padding(8.dp),
                        enabled = recordingState != RecordingState.FINALIZING
                    ) { 
                        Box(
                            Modifier.fillMaxSize()
                                .background(
                                    if (recordingState == RecordingState.RECORDING) Color.Red else Color.Red.copy(alpha = 0.5f), 
                                    if (recordingState == RecordingState.RECORDING) RoundedCornerShape(4.dp) else CircleShape
                                )
                        ) 
                    }
                }
                
                AnimatedVisibility(
                    visible = recordingState != RecordingState.IDLE,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    IconButton(
                        onClick = {
                            // Cancel Recording
                            rearRecording?.stop()
                            frontRecording?.stop()
                            rearRecording = null
                            frontRecording = null
                            
                            // Delete temporary segments
                            scope.launch(Dispatchers.IO) {
                                recordingSegments.forEach { segment ->
                                    val f1 = File(segment.mainPath)
                                    if (f1.exists()) f1.delete()
                                    segment.selfiePath?.let { p ->
                                        val f2 = File(p)
                                        if (f2.exists()) f2.delete()
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    recordingSegments.clear()
                                    startedSegmentsCount.set(0)
                                    finalizedSegmentsCount.set(0)
                                    recordingState = RecordingState.IDLE
                                    isWaitingForFinalization = false
                                    Toast.makeText(context, "Recording cancelled", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }, 
                        modifier = Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                    }
                }
            }
            if (recordingState != RecordingState.IDLE) {
                Column(
                    modifier = Modifier.align(Alignment.TopCenter).offset(y = (-48).dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (recordingState == RecordingState.RECORDING) "Recording" else "Processing video...", 
                        color = Color.Red, 
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (recordingState == RecordingState.RECORDING) {
                        val minutes = (recordingDuration / 60000)
                        val seconds = (recordingDuration % 60000) / 1000
                        Text(
                            text = String.format(Locale.US, "%02d:%02d", minutes, seconds),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        CircularProgressIndicator(color = Color.Red, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

private fun takePhoto(context: Context, capture: ImageCapture, executor: Executor, isFront: Boolean, onMedia: (String, Boolean) -> Unit) {
    capture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(img: ImageProxy) {
            val bitmap = proxyToBitmap(img, isFront)
            if (bitmap != null) {
                val file = File(context.filesDir, "Photo_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                img.close()
                onMedia(file.absolutePath, false)
            } else {
                img.close()
                ContextCompat.getMainExecutor(context).execute {
                    Toast.makeText(context, "Failed to capture photo", Toast.LENGTH_SHORT).show()
                }
            }
        }
        override fun onError(e: ImageCaptureException) { 
            Log.e("DualCamera", "Photo failed", e)
            ContextCompat.getMainExecutor(context).execute {
                Toast.makeText(context, "Photo capture failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    })
}

private fun takeDualPhoto(context: Context, backCapture: ImageCapture, frontCapture: ImageCapture, executor: Executor, onMedia: (String, Boolean) -> Unit) {
    val frontFile = File(context.filesDir, "DualTemp_Front_${System.currentTimeMillis()}.jpg")
    val backFile = File(context.filesDir, "DualTemp_Back_${System.currentTimeMillis()}.jpg")

    fun merge() {
        if (!frontFile.exists() || !backFile.exists()) return
        
        try {
            val frontBmp = BitmapFactory.decodeFile(frontFile.absolutePath)
            val backBmp = BitmapFactory.decodeFile(backFile.absolutePath)
            
            if (frontBmp == null || backBmp == null) {
                ContextCompat.getMainExecutor(context).execute { Toast.makeText(context, "Failed to decode photos", Toast.LENGTH_SHORT).show() }
                return
            }

            val merged = Bitmap.createBitmap(backBmp.width, backBmp.height, backBmp.config ?: Bitmap.Config.ARGB_8888)
            val canvas = Canvas(merged)
            canvas.drawBitmap(backBmp, 0f, 0f, null)
            
            // Mirror the front photo manually since BitmapFactory ignores EXIF mirroring metadata
            val matrix = Matrix()
            matrix.postScale(-1f, 1f, frontBmp.width / 2f, frontBmp.height / 2f)
            val mirroredFront = Bitmap.createBitmap(frontBmp, 0, 0, frontBmp.width, frontBmp.height, matrix, true)
            
            val pipW = backBmp.width / 4
            val pipH = (pipW.toFloat() * frontBmp.height / frontBmp.width).toInt()
            val margin = 40f
            val left = backBmp.width - pipW - margin
            canvas.drawRect(left - 4, margin - 4, left + pipW + 4, margin + pipH + 4, Paint().apply { color = android.graphics.Color.WHITE })
            canvas.drawBitmap(mirroredFront, null, RectF(left, margin, left + pipW, margin + pipH), null)
            
            val file = File(context.filesDir, "DualPhoto_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { merged.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            
            // Clean up
            mirroredFront.recycle()
            frontBmp.recycle()
            backBmp.recycle()
            
            // Clean up temp files
            frontFile.delete()
            backFile.delete()
            
            onMedia(file.absolutePath, false)
        } catch (e: Exception) {
            Log.e("DualCamera", "Merge failed", e)
            ContextCompat.getMainExecutor(context).execute { Toast.makeText(context, "Failed to merge photos", Toast.LENGTH_SHORT).show() }
        }
    }

    // Capture sequentially using OutputFileOptions for better stability
    val frontOptions = ImageCapture.OutputFileOptions.Builder(frontFile).build()
    
    frontCapture.takePicture(frontOptions, executor, object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
            val backOptions = ImageCapture.OutputFileOptions.Builder(backFile).build()
            backCapture.takePicture(backOptions, executor, object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    merge()
                }
                override fun onError(e: ImageCaptureException) {
                    Log.e("DualCamera", "Back capture failed", e)
                    ContextCompat.getMainExecutor(context).execute { Toast.makeText(context, "Back capture failed", Toast.LENGTH_SHORT).show() }
                }
            })
        }
        override fun onError(e: ImageCaptureException) {
            Log.e("DualCamera", "Front capture failed", e)
            ContextCompat.getMainExecutor(context).execute { Toast.makeText(context, "Front capture failed", Toast.LENGTH_SHORT).show() }
        }
    })
}

private fun proxyToBitmap(img: ImageProxy, isFront: Boolean): Bitmap? {
    return try {
        val buffer = img.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val matrix = Matrix()
        matrix.postRotate(img.imageInfo.rotationDegrees.toFloat())
        if (isFront) matrix.postScale(-1f, 1f)
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } catch (e: Exception) {
        Log.e("DualCamera", "Proxy conversion failed", e)
        null
    }
}
