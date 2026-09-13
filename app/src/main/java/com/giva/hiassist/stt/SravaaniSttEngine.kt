package com.giva.hiassist.stt

import android.content.Context
import com.giva.hiassist.domain.Caption
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineNeMoCtcModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext

/**
 * Offline streaming ASR backed by ARTPARK-IISc SraVaani-0.5-live weights,
 * converted to an int8 sherpa-onnx compatible NeMo CTC graph.
 *
 * Model bundle expected in assets/sravaani/:
 *   model-la13.onnx  (att_context_size [70,13])
 *   tokens.txt
 *
 * Audio stays on device. Input is PCM16 mono at 16 kHz from AudioCaptureManager.
 *
 * sherpa-onnx's OnlineStream has no runtime language-switching option, and a
 * single NeMo CTC checkpoint is trained for a fixed language set, so the
 * `language` argument here is accepted for API symmetry with [SttEngine] and
 * surfaced in the UI, but does not change recognizer behavior.
 */
class SravaaniSttEngine(private val context: Context) : SttEngine {
    private val output = MutableSharedFlow<Caption>(extraBufferCapacity = 64)
    override val results: Flow<Caption> = output

    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    private var lastText = ""

    override suspend fun start(language: String) = withContext(Dispatchers.Default) {
        lastText = ""
        if (!assetsPresent()) {
            output.tryEmit(Caption("Offline model bundle is missing from this build.", isFinal = true))
            return@withContext
        }
        release()
        runCatching {
            val modelConfig = OnlineModelConfig(
                neMoCtc = OnlineNeMoCtcModelConfig(model = "$MODEL_DIR/$MODEL_FILE"),
                tokens = "$MODEL_DIR/$TOKENS_FILE",
                numThreads = 4,
                debug = false,
                provider = "cpu",
                modelType = "nemo_ctc"
            )
            val cfg = OnlineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = FEATURE_DIM),
                modelConfig = modelConfig,
                endpointConfig = EndpointConfig(
                    rule1 = EndpointRule(false, 2.4f, 0.0f),
                    rule2 = EndpointRule(true, 1.2f, 0.0f),
                    rule3 = EndpointRule(false, 0.0f, 25.0f)
                ),
                enableEndpoint = true,
                decodingMethod = "greedy_search"
            )
            recognizer = OnlineRecognizer(context.assets, cfg)
            stream = recognizer!!.createStream()
        }.onFailure {
            output.tryEmit(Caption("Unable to initialize SraVaani: ${it.message ?: "unknown error"}", isFinal = true))
        }
    }

    override suspend fun acceptPcm16(samples: ShortArray) = withContext(Dispatchers.Default) {
        val r = recognizer ?: return@withContext
        val s = stream ?: return@withContext
        if (samples.isEmpty()) return@withContext
        val f = FloatArray(samples.size) { i -> samples[i] / 32768.0f }
        s.acceptWaveform(f, SAMPLE_RATE)
        while (r.isReady(s)) r.decode(s)
        val text = r.getResult(s).text.trim()
        if (text.isNotBlank() && text != lastText) {
            lastText = text
            output.tryEmit(Caption(text = text, isFinal = false))
        }
        if (r.isEndpoint(s)) {
            finalizeSegment(r, s)
            r.reset(s)
            lastText = ""
        }
    }

    override suspend fun stop() = withContext(Dispatchers.Default) {
        val r = recognizer
        val s = stream
        if (r != null && s != null) {
            // SraVaani/sherpa requires a padded tail so the last partial chunk is decoded.
            s.acceptWaveform(FloatArray((SAMPLE_RATE * 1.3f).toInt()), SAMPLE_RATE)
            s.inputFinished()
            while (r.isReady(s)) r.decode(s)
            val finalText = r.getResult(s).text.trim()
            if (finalText.isNotBlank()) output.tryEmit(Caption(finalText, isFinal = true))
        }
        release()
    }

    private fun finalizeSegment(r: OnlineRecognizer, s: OnlineStream) {
        val text = r.getResult(s).text.trim()
        if (text.isNotBlank()) output.tryEmit(Caption(text = text, isFinal = true))
    }

    private fun assetsPresent(): Boolean = runCatching {
        val names = context.assets.list(MODEL_DIR)?.toSet().orEmpty()
        MODEL_FILE in names && TOKENS_FILE in names
    }.getOrDefault(false)

    private fun release() {
        runCatching { stream?.release() }
        runCatching { recognizer?.release() }
        stream = null
        recognizer = null
    }

    companion object {
        private const val SAMPLE_RATE = 16000
        // 80-bin log-mel fbank is the sherpa-onnx default for NeMo CTC streaming
        // models. This must match the feature extractor the conversion was
        // exported with; verify against the model's own preprocessor config
        // once the real ONNX/tokens files are available.
        private const val FEATURE_DIM = 80
        private const val MODEL_DIR = "sravaani"
        private const val MODEL_FILE = "model-la13.onnx"
        private const val TOKENS_FILE = "tokens.txt"
    }
}
