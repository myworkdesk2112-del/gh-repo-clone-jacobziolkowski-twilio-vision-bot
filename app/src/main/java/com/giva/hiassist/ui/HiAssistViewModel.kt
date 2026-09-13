package com.giva.hiassist.ui

import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.giva.hiassist.audio.AudioCaptureManager
import com.giva.hiassist.domain.Caption
import com.giva.hiassist.domain.Session
import com.giva.hiassist.domain.SttMode
import com.giva.hiassist.stt.BhashiniSttEngine
import com.giva.hiassist.stt.SravaaniSttEngine
import com.giva.hiassist.stt.SttEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HiAssistViewModel(app: Application) : AndroidViewModel(app) {
    private val audio = AudioCaptureManager(app)
    private var engine: SttEngine = SravaaniSttEngine(app)
    private var resultJob: Job? = null
    private var sessionStart = 0L

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()
    private val _caption = MutableStateFlow(Caption("Ready for live captions", isFinal = true))
    val caption: StateFlow<Caption> = _caption.asStateFlow()
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()
    private val _mode = MutableStateFlow(SttMode.SRAVAANI_ON_DEVICE)
    val mode: StateFlow<SttMode> = _mode.asStateFlow()
    private val _language = MutableStateFlow("hi")
    val language: StateFlow<String> = _language.asStateFlow()
    private val _fontScale = MutableStateFlow(1f)
    val fontScale: StateFlow<Float> = _fontScale.asStateFlow()
    private val _showDirection = MutableStateFlow(true)
    val showDirection: StateFlow<Boolean> = _showDirection.asStateFlow()

    private val current = mutableListOf<Caption>()
    private val _currentCaptions = MutableStateFlow<List<Caption>>(emptyList())
    val currentCaptions: StateFlow<List<Caption>> = _currentCaptions.asStateFlow()
    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()

    fun setMode(mode: SttMode) {
        if (_listening.value || mode == _mode.value) return
        _mode.value = mode
        engine = when (mode) {
            SttMode.SRAVAANI_ON_DEVICE -> SravaaniSttEngine(getApplication())
            SttMode.BHASHINI_CLOUD -> BhashiniSttEngine()
        }
        _caption.value = Caption(if (mode == SttMode.SRAVAANI_ON_DEVICE) "On-device SraVaani ready" else "BHASHINI cloud ready", isFinal = true)
    }

    fun setLanguage(code: String) { if (!_listening.value) _language.value = code }
    fun setFontScale(value: Float) { _fontScale.value = value.coerceIn(.8f, 1.5f) }
    fun setShowDirection(value: Boolean) { _showDirection.value = value }

    fun start() {
        if (_listening.value) return
        _listening.value = true
        sessionStart = System.currentTimeMillis()
        current.clear()
        _currentCaptions.value = emptyList()
        resultJob?.cancel()
        resultJob = viewModelScope.launch {
            engine.results.collect { result ->
                _caption.value = result
                if (result.isFinal && current.lastOrNull()?.text != result.text) { current += result; _currentCaptions.value = current.toList() }
            }
        }
        viewModelScope.launch {
            engine.start(_language.value)
            if (_listening.value) {
                audio.start(viewModelScope,
                    onAudio = { frame -> viewModelScope.launch { engine.acceptPcm16(frame) } },
                    onLevel = { _level.value = it }
                )
            }
        }
    }

    fun stop() {
        if (!_listening.value) return
        _listening.value = false
        audio.stop()
        viewModelScope.launch {
            engine.stop()
            if (current.isNotEmpty()) {
                _sessions.value = listOf(Session(startedAt = sessionStart, endedAt = System.currentTimeMillis(), captions = current.toList())) + _sessions.value
            }
        }
        _level.value = 0f
    }

    fun microphoneGranted(): Boolean = ContextCompat.checkSelfPermission(
        getApplication(), android.Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    override fun onCleared() {
        audio.stop()
        resultJob?.cancel()
        super.onCleared()
    }
}
