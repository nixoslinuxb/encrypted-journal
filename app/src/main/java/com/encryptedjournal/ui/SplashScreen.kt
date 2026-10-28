package com.encryptedjournal.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    // Stage controller:
    // 0: Flipping pages
    // 1: Book resting open
    // 2: Writing text
    // 3: Transition out
    var animationStage by remember { mutableIntStateOf(0) }

    // Animatable angles for 3 flipping pages
    val page1Rotation = remember { Animatable(0f) }
    val page2Rotation = remember { Animatable(0f) }
    val page3Rotation = remember { Animatable(0f) }

    // Typewriter text state
    val fullText = "My Journal"
    var visibleTextCount by remember { mutableIntStateOf(0) }
    val taglineAlpha = remember { Animatable(0f) }
    val bookScale = remember { Animatable(0.85f) }
    val screenAlpha = remember { Animatable(1f) }

    val currentDateStr = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault()))
    }

    LaunchedEffect(Unit) {
        // Step 1: Scale in the journal book
        launch {
            bookScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }

        // Step 2: Page flipping sequence
        delay(350)
        page1Rotation.animateTo(
            targetValue = -180f,
            animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
        )

        delay(120)
        page2Rotation.animateTo(
            targetValue = -180f,
            animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
        )

        delay(140)
        page3Rotation.animateTo(
            targetValue = -180f,
            animationSpec = tween(durationMillis = 500, easing = LinearOutSlowInEasing)
        )

        // Step 3: Stop on open page
        animationStage = 1
        delay(250)

        // Step 4: Write "My Journal" character by character
        animationStage = 2
        for (i in 1..fullText.length) {
            visibleTextCount = i
            delay(110)
        }

        // Fade in date and tagline
        delay(200)
        taglineAlpha.animateTo(1f, animationSpec = tween(600))

        // Pause to appreciate finished journal page
        delay(1100)

        // Step 5: Transition to app
        animationStage = 3
        screenAlpha.animateTo(0f, animationSpec = tween(500))
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = screenAlpha.value }
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E1E2C),
                        Color(0xFF2D2B42),
                        Color(0xFF12121A)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Ambient background glow
        Box(
            modifier = Modifier
                .size(340.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x33D4AF37), // Soft gold glow
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Main Journal Book Container
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = bookScale.value
                        scaleY = bookScale.value
                    }
                    .width(340.dp)
                    .height(230.dp)
                    .shadow(24.dp, shape = RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF4A3525)) // Dark leather cover
                    .padding(8.dp)
            ) {
                // Outer Leather Border
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, Color(0x55D4AF37), RoundedCornerShape(8.dp))
                ) {
                    // Open Journal Pages Layout (Left & Right halves)
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left Page
                        JournalPageSide(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            isLeft = true
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    tint = Color(0xFF8B5A2B),
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "ENCRYPTED\nJOURNAL",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF6E4720),
                                    textAlign = TextAlign.Center,
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        // Spine Center Shadow / Divider
                        Box(
                            modifier = Modifier
                                .width(12.dp)
                                .fillMaxHeight()
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0x66000000),
                                            Color(0x11000000),
                                            Color(0x66000000)
                                        )
                                    )
                                )
                        )

                        // Right Page (where "My Journal" is written)
                        JournalPageSide(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            isLeft = false
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.Top
                            ) {
                                // Date Line
                                if (taglineAlpha.value > 0f) {
                                    Text(
                                        text = currentDateStr,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Serif,
                                        color = Color(0xFF8C7A6B).copy(alpha = taglineAlpha.value)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                } else {
                                    Spacer(modifier = Modifier.height(18.dp))
                                }

                                // Animated "My Journal" Writing
                                val textToShow = fullText.take(visibleTextCount)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = textToShow,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Serif,
                                        fontStyle = FontStyle.Italic,
                                        color = Color(0xFF2C1A0E)
                                    )

                                    // Floating pen cursor while typing
                                    if (animationStage == 2 && visibleTextCount < fullText.length) {
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null,
                                            tint = Color(0xFFD4AF37),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Tagline text
                                if (taglineAlpha.value > 0f) {
                                    Text(
                                        text = "Your private space for thoughts, memories, and daily reflections.",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Serif,
                                        color = Color(0xFF5C493B).copy(alpha = taglineAlpha.value),
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }

                    // Overlay Flipping Pages (3D Rotation around center spine)
                    if (page1Rotation.value > -180f) {
                        FlippingPage(
                            rotation = page1Rotation.value,
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(158.dp)
                                .align(Alignment.CenterEnd)
                        )
                    }

                    if (page2Rotation.value > -180f) {
                        FlippingPage(
                            rotation = page2Rotation.value,
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(158.dp)
                                .align(Alignment.CenterEnd)
                        )
                    }

                    if (page3Rotation.value > -180f) {
                        FlippingPage(
                            rotation = page3Rotation.value,
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(158.dp)
                                .align(Alignment.CenterEnd)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Subtitle status at bottom
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (animationStage >= 2) "Opening Journal..." else "Preparing your pages...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFD4AF37),
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun JournalPageSide(
    modifier: Modifier = Modifier,
    isLeft: Boolean,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(
                RoundedCornerShape(
                    topStart = if (isLeft) 6.dp else 0.dp,
                    bottomStart = if (isLeft) 6.dp else 0.dp,
                    topEnd = if (!isLeft) 6.dp else 0.dp,
                    bottomEnd = if (!isLeft) 6.dp else 0.dp
                )
            )
            .background(Color(0xFFFFFDF5)) // Cream paper color
    ) {
        // Draw horizontal journal rules
        Canvas(modifier = Modifier.fillMaxSize()) {
            val lineSpacing = 22.dp.toPx()
            var currentY = lineSpacing * 1.5f
            val lineCount = (size.height / lineSpacing).toInt()

            repeat(lineCount) {
                drawLine(
                    color = Color(0xFFE2DCD0),
                    start = Offset(0f, currentY),
                    end = Offset(size.width, currentY),
                    strokeWidth = 1f
                )
                currentY += lineSpacing
            }

            // Margin vertical red line
            if (isLeft) {
                drawLine(
                    color = Color(0xFFFFCDD2),
                    start = Offset(size.width - 24.dp.toPx(), 0f),
                    end = Offset(size.width - 24.dp.toPx(), size.height),
                    strokeWidth = 1.5f
                )
            } else {
                drawLine(
                    color = Color(0xFFFFCDD2),
                    start = Offset(24.dp.toPx(), 0f),
                    end = Offset(24.dp.toPx(), size.height),
                    strokeWidth = 1.5f
                )
            }
        }

        content()
    }
}

@Composable
private fun FlippingPage(
    rotation: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 16 * density
                transformOrigin = TransformOrigin(0f, 0.5f) // Flip around left edge (spine)
                shadowElevation = (sin(Math.toRadians(abs(rotation.toDouble()))) * 12).toFloat()
            }
            .clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
            .background(Color(0xFFFAF6EB)) // Cream paper
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val lineSpacing = 22.dp.toPx()
            var currentY = lineSpacing * 1.5f
            val lineCount = (size.height / lineSpacing).toInt()

            repeat(lineCount) {
                drawLine(
                    color = Color(0xFFE5DFD3),
                    start = Offset(0f, currentY),
                    end = Offset(size.width, currentY),
                    strokeWidth = 1f
                )
                currentY += lineSpacing
            }

            // Shading curve based on rotation
            drawRect(
                color = Color.Black.copy(
                    alpha = (sin(Math.toRadians(abs(rotation.toDouble()))) * 0.15).toFloat()
                )
            )
        }
    }
}
