package com.giva.hiassist.stt

import com.giva.hiassist.domain.Caption
import com.giva.hiassist.domain.Direction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class DemoSttEngine : SttEngine {
    private val output = MutableSharedFlow<Caption>(extraBufferCapacity = 16)
    override val results: Flow<Caption> = output
    private var job: Job? = null

    override suspend fun start(language: String) {
        job?.cancel()
        job = CoroutineScope(Dispatchers.Default).launch {
            val scripts = listOf(
                Triple("Speaker 1", Direction.FRONT_LEFT, "Could you send me the revised drawing by this evening?"),
                Triple("Speaker 2", Direction.FRONT_RIGHT, "Yes, I will share the final version after the review."),
                Triple("Speaker 1", Direction.FRONT, "कल presentation कितने बजे शुरू होगी?"),
                Triple("Speaker 2", Direction.RIGHT, "The presentation starts at ten, but we can meet at nine thirty.")
            )
            var i = 0
            while (true) {
                delay(1200)
                val (speaker, direction, finalText) = scripts[i % scripts.size]
                val words = finalText.split(" ")
                var partial = ""
                for (word in words) {
                    partial = if (partial.isBlank()) word else "$partial $word"
                    output.emit(Caption(partial, speaker, direction, false))
                    delay(140)
                }
                output.emit(Caption(finalText, speaker, direction, true))
                delay(900)
                i++
            }
        }
    }

    override suspend fun acceptPcm16(samples: ShortArray) = Unit
    override suspend fun stop() { job?.cancel(); job = null }
}
