# GiVa HiAssist 1.0 — Android Simulator / On-device ASR

Android 12+ Jetpack Compose app for simulating the GiVa HiAssist live-caption experience.

## Final architecture

- UI: Jetpack Compose, adapted from the public Even Realities software design guidelines supplied for this project.
- Primary ASR: ARTPARK-IISc SraVaani-0.5-live weights through the public int8 ONNX conversion `mobilebytesensei/betterflow-sravaani-streaming-onnx` and sherpa-onnx.
- Audio: 16 kHz mono signed PCM16 using Android `AudioRecord`.
- Privacy: SraVaani mode keeps microphone audio on the device.
- Optional fallback: BHASHINI WebSocket STT when a user-provided API key is configured.
- Target ABI: arm64-v8a.
- Minimum Android: 12 (API 31).

## UI mapping

The implementation intentionally preserves the supplied Even Realities visual language while replacing product-specific functions with HiAssist functions:

- 393 dp reference width, responsive on Android
- #EEEEEE primary background
- white cards, 6 dp corner radius
- 12 dp outer margins / 16 dp card padding
- #232323 primary text / #7B7B7B secondary text
- compact 17 / 15 / 13 / 11 sp type hierarchy
- Live / Transcript paired tabs with selected underline
- conversation-status card, transcription card, 48 dp CTAs
- settings use grouped list cards, toggles, detail rows and slider conventions

The proprietary/reference font is not bundled. Android system sans is used at the same size/weight hierarchy.

## Embedded model

Gradle task `downloadSravaaniModel` runs before `preBuild` and downloads:

- `model-la13.onnx` — 658,699,885 bytes
- `tokens.txt` — 68,907 bytes

The assets are packaged into the generated APK. This keeps source control manageable while producing a genuinely self-contained runtime APK.

The conversion is derived from ARTPARK-IISc/SraVaani-0.5-live without fine-tuning or distillation. See `MODEL_NOTICE.md`.

## Build

Requires JDK 17, Android SDK 35 and network access for Gradle/JitPack/Hugging Face dependencies.

```bash
gradle :app:assembleDebug
```

Output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

A GitHub Actions workflow is included at `.github/workflows/build-apk.yml`; it installs the Android SDK, embeds the model and exports the APK as a build artifact.

## BHASHINI fallback (optional)

Set these as Gradle properties if cloud fallback is needed:

```properties
BHASHINI_API_KEY=...
BHASHINI_BASE_URL=wss://tts.bhashini.ai/stt/stream
BHASHINI_PIPELINE_ID=...
```

Do not commit private API credentials.

## Current hardware limitation

Speaker-direction cues are a simulator affordance on a single-phone microphone. True direction-of-arrival and robust speaker diarization require the intended multi-microphone wearable hardware or additional audio-processing models.
