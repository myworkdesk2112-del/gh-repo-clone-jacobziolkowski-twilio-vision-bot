# Final Build Report — GiVa HiAssist

## Bottom line

**No APK was produced.** This environment (a sandboxed remote container) cannot
complete the build: its network egress policy blocks every host the build
needs (Google's Maven repository, JitPack, and Hugging Face), and it has no
Android SDK installed with no way to install one. These are environment
restrictions, not code defects, and they are demonstrated with real command
output below, not assumed. Everything that *could* be done without those
resources — the audit, code fixes, and Gradle wrapper — was done. Nothing here
should be read as "the project doesn't build"; it is "this specific sandboxed
session cannot build it."

## Environment constraints (with evidence)

1. **No Android SDK is installed**, and none can be installed: the SDK
   manager fetches packages from `dl.google.com`, which is blocked (see #2).
   `ANDROID_HOME`/`local.properties` has nothing to point at.
2. **`dl.google.com` is blocked by network policy** (HTTP 403 on CONNECT).
   This is exactly the host Gradle's `google()` repository resolves plugin
   and library artifacts against. Actual failure captured with `--info`:

   ```
   Failed to get resource: GET. [HTTP HTTP/1.1 403 Forbidden:
   https://dl.google.com/dl/android/maven2/com/android/application/
   com.android.application.gradle.plugin/8.7.3/
   com.android.application.gradle.plugin-8.7.3.pom]
   ```

   This means the Android Gradle Plugin itself — and every AndroidX/Compose
   dependency — cannot be resolved in this session, independent of anything
   in this repository.
3. **`jitpack.io` is blocked** (HTTP 403 on CONNECT). `sherpa-onnx` is
   declared as `com.github.k2-fsa:sherpa-onnx:v1.13.4` via JitPack, so even if
   the Android SDK were present, this dependency could not be fetched here.
4. **`huggingface.co` is blocked** (HTTP 403 on CONNECT), so the
   `downloadSravaaniModel` Gradle task cannot fetch `model-la13.onnx`
   (658,699,885 bytes) or `tokens.txt` (68,907 bytes) in this session.

None of these are things a code change can route around from inside the
sandbox — they are organization-level egress rules on this session. A build
machine with normal internet access (a laptop, a self-hosted runner, or the
project's own GitHub Actions workflow at `.github/workflows/build-apk.yml`,
which runs on a normal GitHub-hosted runner with unrestricted internet) does
not have any of these restrictions and should be able to run
`./gradlew --no-daemon clean :app:assembleDebug` end to end.

## What was actually done here

### 1. Audit
Read every file the handoff `CLAUDE.md` called out: `app/build.gradle.kts`,
`SravaaniSttEngine.kt`, `AudioCaptureManager.kt`, `HiAssistViewModel.kt`,
`MainActivity.kt`, the manifest, the CI workflow, and the docs.

### 2. Gradle wrapper (missing from the handoff ZIP)
Generated a real Gradle 8.9 wrapper (`gradlew`, `gradlew.bat`,
`gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`)
using the Gradle binary preinstalled in this container, and verified
`./gradlew` actually launches and reaches the project's build scripts (it
fails only at Android-plugin resolution, per the constraints above — proof
the wrapper itself is sound).

### 3. Code fix: `SravaaniSttEngine.kt` called a method that doesn't exist
The handoff code called `stream.setOption("language", languageCode)` after
`createStream()` and again after `reset()`. sherpa-onnx's Kotlin
`OnlineStream` API has no `setOption` method — this would have failed to
compile against the real sherpa-onnx AAR. Since a single NeMo CTC checkpoint
like SraVaani-0.5-live is trained for a fixed language set and has no
per-stream runtime language switch in sherpa-onnx's API, the calls were
removed rather than replaced, and this is documented in a class-level comment
so a future contributor doesn't reintroduce it expecting real language
switching from the engine (language selection remains a real, working UI/state
concept in `HiAssistViewModel`, it just doesn't reconfigure the recognizer).

### 4. Risk flagged, not silently changed: feature dimension
The handoff code set `featureDim = 128` in `FeatureConfig`. The sherpa-onnx
default for NeMo CTC streaming models is an 80-bin log-mel filterbank; 128 is
unusual and, if wrong, the recognizer will still initialize but produce
garbage output (dimension mismatches are a runtime silent-failure mode in
NeMo CTC feature extraction, not always a startup crash). It was changed to
`80` with a comment to verify it against the model's own preprocessor config
once the real ONNX file can be downloaded and inspected — this could not be
confirmed here because the model file itself is unreachable (see constraints).
**This is the single highest-risk unresolved item**: it should be checked
against `mobilebytesensei/betterflow-sravaani-streaming-onnx`'s published
config before shipping.

### 5. Model download task made robust for a ~659 MB file
The original `downloadSravaaniModel` task used a bare
`URL(url).openStream()` with no timeout, no retry, and no resume — a single
transient network blip on a 659 MB transfer would fail the whole build with
no way to continue from where it left off. It now:
- Sets explicit connect/read timeouts and a `User-Agent` header.
- Retries up to 5 times with linear backoff.
- Resumes a partial download with an HTTP `Range` request instead of
  restarting from zero.
- Still verifies exact byte length before accepting the file, per the
  handoff's pinned sizes.

### 6. Checklist updated
`HANDOFF_CHECKLIST.md` reflects true status — audited/fixed items checked,
network- and SDK-blocked items explicitly left unchecked with the reason.

## Required validation — status

### Static/build checks
- `./gradlew :app:assembleDebug` — **not completed**: fails at Android
  plugin resolution (`dl.google.com` blocked). Full error captured above.
- `./gradlew test` / `./gradlew lintDebug` — **not run**: both require the
  Android Gradle Plugin, which cannot be resolved here.
- APK asset/ABI inspection, SHA-256, and size — **not applicable**: no APK
  was produced.

### Runtime checks
**Not tested.** This container has no Android emulator or physical device
attached. Nothing below was attempted, and nothing below should be assumed to
work: install, launch, microphone permission grant, SraVaani initialization,
live Hindi/Hinglish captioning, session persistence, or Settings navigation.

## Definition-of-done checklist (per `CLAUDE.md`)

1. Corrected source project — **done** (this repository).
2. Successful build log summary — **not applicable**, build did not succeed;
   exact failure point and cause are documented above.
3. Installable `app-debug.apk` — **not produced**; environment prevents
   binary build, as required to state explicitly.
4. APK SHA-256 and size — **not applicable**.
5. Confirmation model + tokens are embedded — **not applicable**, model
   could not be downloaded (`huggingface.co` blocked).
6. List of code/build fixes made — see "What was actually done here" above.
7. Runtime validation status — **all untested**, explicitly separated from
   any claim of passing.
8. Remaining blocker needing a physical Android device — **all runtime
   checks** in the "Required validation" section need a device or emulator.

## What finishing this needs

On a machine with normal internet access and either a local Android SDK or
`sdkmanager` access to `dl.google.com`:

```bash
./gradlew --no-daemon clean :app:assembleDebug
./gradlew lintDebug
./gradlew test
```

The included `.github/workflows/build-apk.yml` already does this on a
GitHub-hosted runner (unrestricted internet, SDK installed via
`android-actions/setup-android` + `sdkmanager`) and should be the fastest way
to get a real `app-debug.apk`, its SHA-256, and lint/test output without
depending on this sandboxed session's network policy.
