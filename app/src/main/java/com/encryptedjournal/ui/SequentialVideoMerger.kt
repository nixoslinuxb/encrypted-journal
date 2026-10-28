package com.encryptedjournal.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ExportException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

data class VideoSegment(
    val mainPath: String,
    val selfiePath: String? = null,
    var durationUs: Long = 0L
)

object SequentialVideoMerger {
    @OptIn(UnstableApi::class)
    suspend fun mergeVideoSegments(
        context: Context,
        segments: List<VideoSegment>,
        outputPath: String,
        onComplete: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (segments.isEmpty()) {
            onError(IllegalArgumentException("No video segments to merge"))
            return
        }

        // Wait slightly for any final writes to complete
        delay(800.milliseconds)

        val mainOutputPath = outputPath
        val selfieOutputPath = outputPath.replace(".mp4", "_selfie.mp4")

        // Merge Main Track
        val mainMergeSuccess = mergeTrack(
            context = context,
            items = segments.map { it.mainPath to 0L },
            outputPath = mainOutputPath
        )

        if (!mainMergeSuccess) {
            onError(Exception("Failed to merge main track"))
            return
        }

        // Merge Selfie Track (with gaps)
        val hasAnySelfie = segments.any { it.selfiePath != null }
        if (hasAnySelfie) {
            val selfieMergeSuccess = mergeTrack(
                context = context,
                items = segments.map { it.selfiePath to it.durationUs },
                outputPath = selfieOutputPath
            )
            
            if (selfieMergeSuccess) {
                // Success: return both paths
                onComplete("$mainOutputPath|$selfieOutputPath")
            } else {
                onComplete(mainOutputPath) // Fallback to just main
            }
        } else {
            // Just main
            onComplete(mainOutputPath)
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun mergeTrack(
        context: Context,
        items: List<Pair<String?, Long>>, // path to durationUs (if path is null, it's a gap)
        outputPath: String
    ): Boolean = withContext(Dispatchers.Main) {
        val transformer = Transformer.Builder(context).build()
        
        val editedMediaItems = items.map { (path, duration) ->
            if (path != null) {
                val mediaItem = MediaItem.fromUri(Uri.fromFile(File(path)))
                EditedMediaItem.Builder(mediaItem).setRemoveAudio(false).build()
            } else {
                // Create a gap
                val gapItem = MediaItem.Builder().setMediaId("gap").build()
                EditedMediaItem.Builder(gapItem).setDurationUs(duration).build()
            }
        }

        val sequence = EditedMediaItemSequence.withAudioAndVideoFrom(editedMediaItems)
        val composition = Composition.Builder(listOf(sequence)).build()

        val latch = CompletableDeferred<Boolean>()

        transformer.addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                latch.complete(true)
            }

            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                Log.e("SequentialVideoMerger", "Merge failed", exportException)
                latch.complete(false)
            }
        })

        try {
            val file = File(outputPath)
            if (file.exists()) file.delete()
            transformer.start(composition, outputPath)
            val success = latch.await()
            
            // Clean up source segments for this track
            items.forEach { (path, _) -> path?.let { File(it).delete() } }
            
            success
        } catch (e: Exception) {
            Log.e("SequentialVideoMerger", "Start failed", e)
            false
        }
    }
}
