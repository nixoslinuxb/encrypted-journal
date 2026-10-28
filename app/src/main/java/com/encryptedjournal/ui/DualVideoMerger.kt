package com.encryptedjournal.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Size
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ExportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.FileNotFoundException

object DualVideoMerger {
    @OptIn(UnstableApi::class)
    suspend fun mergeVideos(
        context: Context,
        mainPath: String,
        frontPath: String,
        outputPath: String
    ): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val mainFile = File(mainPath)
            val frontFile = File(frontPath)

            if (!mainFile.exists() || !frontFile.exists()) {
                continuation.resumeWithException(FileNotFoundException("Source video files not found"))
                return@suspendCancellableCoroutine
            }

            val transformer = Transformer.Builder(context)
                .build()

            val mainMediaItem = MediaItem.fromUri(Uri.fromFile(mainFile))
            val frontMediaItem = MediaItem.fromUri(Uri.fromFile(frontFile))

            // Background: Main camera
            val mainSequence = EditedMediaItemSequence.Builder(EditedMediaItem.Builder(mainMediaItem).build()).build()

            // Overlay: Front camera
            val frontEditedItem = EditedMediaItem.Builder(frontMediaItem)
                .setRemoveAudio(true)
                .build()
            val frontSequence = EditedMediaItemSequence.Builder(frontEditedItem).build()

            // Configure PiP layout using VideoCompositorSettings
            val pipSettings = object : VideoCompositorSettings {
                override fun getOutputSize(inputSizes: List<Size>): Size {
                    return if (inputSizes.isNotEmpty()) inputSizes[0] else Size(1280, 720)
                }

                override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
                    return if (inputId == 1) { // Selfie/Front camera (Index 1 in Composition)
                        StaticOverlaySettings.Builder()
                            .setScale(-0.3f, 0.3f) // 30% scale, mirrored horizontally
                            .setOverlayFrameAnchor(0f, 0f) // Center anchor
                            .setBackgroundFrameAnchor(0.7f, 0.7f) // Positioned in top-right quadrant
                            .build()
                    } else { // Background / Main camera (Index 0)
                        StaticOverlaySettings.Builder()
                            .setScale(1f, 1f)
                            .setOverlayFrameAnchor(0f, 0f)
                            .setBackgroundFrameAnchor(0f, 0f)
                            .build()
                    }
                }
            }

            val composition = Composition.Builder(listOf(mainSequence, frontSequence))
                .setVideoCompositorSettings(pipSettings)
                .build()

            transformer.addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (continuation.isActive) continuation.resume(outputPath)
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    Log.e("DualVideoMerger", "Export failed", exportException)
                    if (continuation.isActive) continuation.resumeWithException(exportException)
                }
            })

            try {
                val file = File(outputPath)
                if (file.exists()) file.delete()
                transformer.start(composition, outputPath)
            } catch (e: Exception) {
                Log.e("DualVideoMerger", "Start failed", e)
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            continuation.invokeOnCancellation {
                transformer.cancel()
            }
        }
    }
}
