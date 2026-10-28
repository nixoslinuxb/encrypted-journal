package com.encryptedjournal.ui

import android.util.Log
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

@Composable
fun QrScannerScreen(
    onResult: (String) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler {
        onDismiss()
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    var hasFlash by remember { mutableStateOf(false) }
    var isFlashOn by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }

    val previewView = remember { PreviewView(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    val laserProgress by rememberInfiniteTransition(label = "laser_anim").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    LaunchedEffect(Unit) {
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build()
        val selector = CameraSelector.DEFAULT_BACK_CAMERA

        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1280, 720),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        analysis.setAnalyzer(analysisExecutor, QrScannerManager.createAnalyzer { result ->
            onResult(result)
        })

        try {
            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                selector,
                preview,
                analysis
            )
            cameraControl = camera.cameraControl
            hasFlash = camera.cameraInfo.hasFlashUnit()
            preview.surfaceProvider = previewView.surfaceProvider
        } catch (e: Exception) {
            Log.e("QrScanner", "Binding failed", e)
        }
    }

    var totalDragAmount by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        // Detect swipe right starting from left edge
                        totalDragAmount = if (offset.x < 200f) 0f else -1000f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        if (totalDragAmount >= 0f) {
                            totalDragAmount += dragAmount
                            if (totalDragAmount > 120f) {
                                onDismiss()
                                totalDragAmount = -1000f
                            }
                        }
                    },
                    onDragEnd = { totalDragAmount = 0f },
                    onDragCancel = { totalDragAmount = 0f }
                )
            }
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        val primaryColor = MaterialTheme.colorScheme.primary

        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val boxSize = 260.dp.toPx()

            val left = (canvasWidth - boxSize) / 2
            val top = (canvasHeight - boxSize) / 2
            val right = left + boxSize
            val bottom = top + boxSize
            val rect = Rect(left, top, right, bottom)
            val cornerLen = 28.dp.toPx()
            val strokeW = 4.dp.toPx()

            drawPath(
                path = Path().apply {
                    addRoundRect(RoundRect(0f, 0f, canvasWidth, canvasHeight, CornerRadius.Zero))
                    addRoundRect(RoundRect(rect, CornerRadius(20.dp.toPx())))
                },
                color = Color.Black.copy(alpha = 0.65f),
                blendMode = BlendMode.SrcOut
            )

            drawRoundRect(
                color = Color.White.copy(alpha = 0.2f),
                topLeft = Offset(left, top),
                size = size.copy(width = boxSize, height = boxSize),
                cornerRadius = CornerRadius(20.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Top-Left Corner
            drawPath(
                path = Path().apply {
                    moveTo(left, top + cornerLen)
                    lineTo(left, top + 10)
                    quadraticTo(left, top, left + 10, top)
                    lineTo(left + cornerLen, top)
                },
                color = primaryColor,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )

            // Top-Right Corner
            drawPath(
                path = Path().apply {
                    moveTo(right - cornerLen, top)
                    lineTo(right - 10, top)
                    quadraticTo(right, top, right, top + 10)
                    lineTo(right, top + cornerLen)
                },
                color = primaryColor,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )

            // Bottom-Left Corner
            drawPath(
                path = Path().apply {
                    moveTo(left, bottom - cornerLen)
                    lineTo(left, bottom - 10)
                    quadraticTo(left, bottom, left + 10, bottom)
                    lineTo(left + cornerLen, bottom)
                },
                color = primaryColor,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )

            // Bottom-Right Corner
            drawPath(
                path = Path().apply {
                    moveTo(right - cornerLen, bottom)
                    lineTo(right - 10, bottom)
                    quadraticTo(right, bottom, right, bottom - 10)
                    lineTo(right, bottom - cornerLen)
                },
                color = primaryColor,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )

            // Animated Laser Beam Line
            val laserY = top + (boxSize * laserProgress)
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.1f),
                        primaryColor,
                        primaryColor.copy(alpha = 0.1f)
                    )
                ),
                start = Offset(left + 12.dp.toPx(), laserY),
                end = Offset(right - 12.dp.toPx(), laserY),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Top Control Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "QR Scanner",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            if (hasFlash) {
                IconButton(
                    onClick = {
                        isFlashOn = !isFlashOn
                        cameraControl?.enableTorch(isFlashOn)
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isFlashOn) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Flash",
                        tint = Color.White
                    )
                }
            } else {
                Spacer(Modifier.size(44.dp))
            }
        }

        // Bottom Instruction Pill
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.Black.copy(alpha = 0.65f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        ) {
            Text(
                text = "Align QR code inside frame to scan",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }
    }
}
