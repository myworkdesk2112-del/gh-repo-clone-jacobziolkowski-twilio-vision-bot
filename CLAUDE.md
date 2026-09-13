# Claude Handoff — GiVa HiAssist Android APK

## Mission
Finish, validate, and build this Android project into a **working installable APK** with the **SraVaani on-device speech model embedded**. Do not return only code suggestions. Work directly on the project until `assembleDebug` succeeds, then provide the APK and a concise validation report.

## Product
GiVa HiAssist is an Android simulator for a smart-glasses live-caption product. The phone microphone supplies live audio; the app performs streaming speech-to-text and presents captions using the visual language of the supplied Even Realities public software design guideline.

### Figma reference
https://www.figma.com/design/92mI0ghNGF0jviGxqhM2Ym/Even-Realities---Software-Design-Guidelines--Public---Copy-?node-id=2922-80782&p=f&t=TTgNoS6eTnHy9qZh-0

The current implementation was adapted from the Figma **Conversate** and **Settings** screens. Preserve this design language; do not replace it with generic Material styling.

## Visual design requirements
- Primary background: `#EEEEEE`
- Card background: `#FFFFFF`
- Primary text: `#232323`
- Secondary text: `#7B7B7B`
- Divider: `#E4E4E4`
- Cards: 6 dp radius
- Main outer margin: 12 dp
- Card padding: 16 dp
- CTA height: 48 dp
- Type hierarchy: 17 / 15 / 13 / 11 sp, compact tracking
- Figma reference font is FK Grotesk Neue, but it is **not bundled**. Do not download or redistribute proprietary font files. Use Android system sans or a compatible open font only if necessary.
- Keep Live / Transcript tab pattern, compact navigation header, conversation status card, white transcript/live card, grouped Settings rows.

## Functional requirements
The final APK must provide:
1. Android 12+ support (minSdk 31).
2. `arm64-v8a` target.
3. Runtime microphone permission request.
4. 16 kHz mono PCM16 recording.
5. **Primary STT: offline SraVaani-0.5-live**, running on-device.
6. Model files packaged inside the APK/AAB assets at build time.
7. Streaming partial captions while listening.
8. Finalized caption segments on endpoint / stop.
9. Live screen, Transcript screen, Sessions screen, Settings screen.
10. Languages exposed in UI: Hindi/Hinglish, Gujarati, Bengali, Marathi, Malayalam, Telugu, Tamil, Kannada, Punjabi.
11. Caption-size control.
12. Direction indicator is only a simulator affordance on a phone microphone; do not claim real DOA.
13. Optional BHASHINI cloud mode must not block the offline build and must not contain hard-coded credentials.
14. No audio should leave the device in SraVaani mode.

## Primary model
Source project/model:
- ARTPARK-IISc SraVaani-0.5-live
- ONNX conversion currently referenced: `mobilebytesensei/betterflow-sravaani-streaming-onnx`

Expected downloaded build assets:
- `app/src/main/assets/sravaani/model-la13.onnx`
- expected byte length: **658,699,885**
- `app/src/main/assets/sravaani/tokens.txt`
- expected byte length: **68,907**

The current Gradle task `downloadSravaaniModel` downloads and verifies these files before `preBuild`.

## Existing stack
- Kotlin
- Jetpack Compose
- Android Gradle Plugin 8.7.3
- Kotlin 2.0.21
- JDK 17
- compileSdk / targetSdk 35
- sherpa-onnx Android runtime
- OkHttp for optional BHASHINI fallback
- Coroutines / StateFlow

## Your first job: audit before changing
Inspect every source/build file. Do not assume the current source compiles. In particular verify:
- sherpa-onnx dependency coordinates/version resolve correctly
- Kotlin API calls in `SravaaniSttEngine.kt` match the exact installed sherpa-onnx Android Kotlin API
- NeMo online CTC config is correct for the selected model
- feature dimension / sample rate / endpoint calls / stream option calls are valid
- JNI/native libraries are packaged for arm64
- model asset paths are correct when passed to sherpa-onnx
- Android manifest / Compose code compiles
- no missing Gradle wrapper files prevent local/CI build
- model download is robust enough for a ~659 MB file
- APK packaging does not compress/corrupt ONNX assets
- heap/runtime implications are handled safely

If the existing sherpa-onnx artifact or API is wrong, correct it using upstream documentation/source. Preserve the model choice unless there is a concrete incompatibility; if a conversion must change, document exactly why and use a SraVaani streaming-compatible conversion.

## Build requirements
Create/repair a standard Gradle wrapper if missing and make these commands work from project root:

```bash
./gradlew --no-daemon clean :app:assembleDebug
```

Expected artifact:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Also ensure `.github/workflows/build-apk.yml` works on a clean GitHub runner.

## Required validation
Do not stop at `BUILD SUCCESSFUL`. Perform as much of the following as your environment allows:

### Static/build checks
- `./gradlew :app:assembleDebug`
- `./gradlew test` if tests exist / add focused unit tests where useful
- `./gradlew lintDebug`
- inspect APK contents and confirm both model and token assets are present
- inspect APK ABI contents and confirm arm64 native library presence
- report final APK file size and SHA-256

### Runtime checks
If emulator/device access is available:
- install APK
- launch app
- grant microphone permission
- start SraVaani mode
- confirm recognizer initialization does not crash
- speak a short Hindi/Hinglish phrase and confirm partial/final captions appear
- stop session and verify transcript/session persistence for the running app session
- navigate Settings and modify language/caption size

If hardware/device validation is not available, say so explicitly and do not claim it passed.

## UX rules
You may edit functional details, tabs, and page hierarchy where needed for reliability/usability, but **keep the Even Realities visual style**. Prefer a restrained accessibility-first interface. Avoid decorative gradients, large Material cards, floating action buttons, or visual language not present in the reference.

Recommended final information architecture:
- Live
- Transcript
- Sessions
- Settings

The primary task is **live captions**, so it must remain the shortest path after launch.

## Known product constraint
True speaker direction / multi-speaker direction-of-arrival cannot be derived reliably from a normal phone's single effective microphone path. Treat direction UI as simulated/demo-only unless actual multi-mic processing is implemented and validated.

## Files you should inspect closely
- `app/build.gradle.kts`
- `app/src/main/java/com/giva/hiassist/stt/SravaaniSttEngine.kt`
- `app/src/main/java/com/giva/hiassist/audio/AudioCaptureManager.kt`
- `app/src/main/java/com/giva/hiassist/ui/HiAssistViewModel.kt`
- `app/src/main/java/com/giva/hiassist/MainActivity.kt`
- `.github/workflows/build-apk.yml`
- `README.md`
- `MODEL_NOTICE.md`

## Do not do these
- Do not remove SraVaani and replace it with a fake/demo transcription engine in the final build.
- Do not make cloud STT mandatory.
- Do not commit private API keys.
- Do not claim multilingual accuracy or device performance without testing.
- Do not redistribute proprietary Figma/reference fonts.
- Do not return a source-only answer if your environment can actually build the APK.
- Do not silently downgrade to a different model.

## Definition of done
The task is complete only when you deliver:
1. Corrected source project.
2. Successful build log summary.
3. Installable `app-debug.apk` (or explicitly state the environment prevents binary build).
4. APK SHA-256 and size.
5. Confirmation model + tokens are embedded.
6. List of code/build fixes made.
7. Runtime validation status, clearly separating tested vs not tested.
8. Any remaining blocker that needs a physical Android device.

When you finish, create a compact `FINAL_BUILD_REPORT.md` in the repository with these results.
