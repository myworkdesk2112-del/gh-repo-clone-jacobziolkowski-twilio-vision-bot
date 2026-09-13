package com.giva.hiassist.stt

import com.giva.hiassist.domain.Caption
import kotlinx.coroutines.flow.Flow

interface SttEngine {
    val results: Flow<Caption>
    suspend fun start(language: String)
    suspend fun acceptPcm16(samples: ShortArray)
    suspend fun stop()
}
