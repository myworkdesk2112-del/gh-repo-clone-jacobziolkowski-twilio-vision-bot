package com.giva.hiassist.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class AudioCaptureManager(private val context: Context) {
    private var recorder: AudioRecord? = null
    private var job: Job? = null

    val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start(
        scope: CoroutineScope,
        onAudio: (ShortArray) -> Unit,
        onLevel: (Float) -> Unit
    ) {
        if (!hasPermission || job != null) return
        val sampleRate = 16_000
        val min = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(min, sampleRate / 2)
        recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 2
        ).also { it.startRecording() }

        job = scope.launch(Dispatchers.IO) {
            val buffer = ShortArray(bufferSize)
            while (isActive) {
                val n = recorder?.read(buffer, 0, buffer.size) ?: 0
                if (n > 0) {
                    val frame = buffer.copyOf(n)
                    onAudio(frame)
                    var sum = 0.0
                    for (s in frame) sum += s.toDouble() * s.toDouble()
                    val rms = sqrt(sum / n).toFloat() / Short.MAX_VALUE
                    onLevel((rms * 8f).coerceIn(0f, 1f))
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        runCatching { recorder?.stop() }
        recorder?.release()
        recorder = null
    }
}
