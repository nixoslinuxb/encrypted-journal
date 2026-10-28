package com.encryptedjournal.ui

import android.media.MediaPlayer
import java.io.File

class VoicePlayer {
    private var mediaPlayer: MediaPlayer? = null

    fun play(file: File, onCompletion: () -> Unit) {
        stop()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            start()
            setOnCompletionListener {
                onCompletion()
                stop()
            }
        }
    }

    fun stop() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
