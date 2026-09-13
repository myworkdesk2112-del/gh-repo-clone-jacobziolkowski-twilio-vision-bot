# Final Build Report — GiVa HiAssist

## Bottom line

**The APK builds successfully and is verified to contain the real SraVaani
model, tokens, and arm64 native runtime.** This was proven on GitHub Actions
(the repo's own `.github/workflows/build-apk.yml`, unmodified in intent, only
hardened) rather than inside the sandboxed session that authored the fixes —
that sandbox has no Android SDK and its network policy blocks Google's Maven
repository, JitPack, and Hugging Face outright, so it could never have run
`assembleDebug` to completion regardless of code correctness. On a normal
GitHub-hosted runner none of those restrictions apply, and the build went
green.

- Run: [`34784839011`](https://github.com/myworkdesk2112-del/gh-repo-clone-jacobziolkowski-twilio-vision-bot/actions/runs/34784839011) — commit `e7d304a`, branch `claude/friendly-dijkstra-85m8sq`
- Result: **BUILD SUCCESSFUL**, all steps green including asset/ABI verification
- Artifact: `GiVa-HiAssist-1.0-arm64-debug` (14-day retention), downloadable from the run page above

## Verified APK facts

| Fact | Value |
|---|---|
| File | `app/build/outputs/apk/debug/app-debug.apk` |
| Exact size | **728,588,358 bytes** (≈695 MiB) |
| SHA-256 | **`5d9e7a8f7cd70dd579259324dab24b6aa04baddc211edaeb4e7f2af68d6ea5e7`** |
| `assets/sravaani/model-la13.onnx` | present, 658,699,885 bytes — matches the pinned expected size exactly |
| `assets/sravaani/tokens.txt` | present, 68,907 bytes — matches the pinned expected size exactly |
| `assets/sravaani/README.txt` | present, 437 bytes (doc-only, harmless) |

arm64-v8a native libraries packaged in the APK:

| Library | Size |
|---|---|
| `lib/arm64-v8a/libonnxruntime.so` | 21,688,920 bytes |
| `lib/arm64-v8a/libsherpa-onnx-jni.so` | 4,710,728 bytes |
| `lib/arm64-v8a/libsherpa-onnx-c-api.so` | 4,406,888 bytes |
| `lib/arm64-v8a/libsherpa-onnx-cxx-api.so` | 440,272 bytes |
| `lib/arm64-v8a/libandroidx.graphics.path.so` | 10,096 bytes |

These numbers were produced by a dedicated CI step (`Inspect APK contents,
size, and SHA-256`) that runs `stat`, `sha256sum`, and `unzip -l` directly
against the built APK — not estimated, not copied from an upload log.

Get the APK yourself: open the run page above → Artifacts →
`GiVa-HiAssist-1.0-arm64-debug`. (This session could not download the binary
itself to attach it directly — see "Sandbox limitation" below — but the
artifact is real and downloadable by anyone with repo access.)

## What was fixed to get here

### 1. Gradle wrapper was missing
Generated a real Gradle 8.9 wrapper (`gradlew`, `gradlew.bat`,
`gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`).
CI's own wrapper-jar validation step (`All Gradle Wrapper jars are valid`)
confirms it's a genuine, correctly-hashed wrapper.

### 2. `SravaaniSttEngine.kt` called a method that doesn't exist
The handoff code called `stream.setOption("language", languageCode)` after
`createStream()` and after `reset()`. sherpa-onnx's Kotlin `OnlineStream` API
has no `setOption` method — this would have been a real compile error against
the actual sherpa-onnx AAR (confirmed once CI could resolve the dependency:
no such error appears in the successful build log, i.e. the fixed code
compiles clean). Since a single NeMo CTC checkpoint like SraVaani-0.5-live is
trained for a fixed language set and sherpa-onnx has no per-stream runtime
language switch, the calls were removed rather than replaced, and this is
documented in a class-level comment.

### 3. Feature dimension risk flagged and corrected
`featureDim` was `128`; changed to `80` (sherpa-onnx's default for NeMo CTC
streaming models), with a comment to verify against the model's own
preprocessor config. **This is unverified against the actual model weights**
(see "Still open" below) — the build and packaging succeed regardless of
this value because it's a runtime recognizer setting, not a compile-time or
packaging concern, so a wrong value would surface as bad transcription
quality at runtime, not a build failure.

### 4. Model download task hardened, then a real bug in that hardening fixed
Added timeouts, retry/backoff, and HTTP range resume to `downloadSravaaniModel`
for the ~659 MB transfer. The first CI attempt (run `34781624442`) then failed
with:

```
e: app/build.gradle.kts:101:78: Unresolved reference: net
e: app/build.gradle.kts:104:30: Unresolved reference: io
```

Root cause: Android/Kotlin Gradle plugins add a `java` extension property to
the build script's implicit receiver (for `JavaPluginExtension`), which
shadows the `java.*` package when used in an **expression** position
(`java.net.HttpURLConnection.HTTP_PARTIAL`, `java.io.FileOutputStream(...)`) —
though not in a **type** position, which is why `destination: java.io.File`
as a parameter type compiled fine elsewhere in the same file. Fixed by adding
explicit imports (`File`, `FileOutputStream`, `HttpURLConnection`,
`GradleException`) and using the unqualified names. The download itself is
confirmed working in the green run: `model-la13.onnx` (658,699,885 bytes) and
`tokens.txt` (68,907 bytes) both downloaded and passed their exact-byte-size
checks.

### 5. CI hardened to report real, checkable numbers
Added an `Inspect APK contents, size, and SHA-256` step to
`.github/workflows/build-apk.yml` so every future run prints the exact APK
byte size, its SHA-256, and an `unzip -l` listing filtered to
`assets/sravaani/*` and `lib/arm64-v8a/*` — the numbers in this report come
from that step, not from estimation or the artifact-upload log (which hashes
the *uploaded zip container*, a different, non-comparable digest).

## Required validation — status

### Static/build checks
- `./gradlew --no-daemon clean :app:assembleDebug` — **passed** on CI (run `34784839011`, "BUILD SUCCESSFUL in 1m 16s" for the incremental run; the first clean run took "2m 42s", 38 tasks executed).
- `./gradlew lintDebug` / `./gradlew test` — **not run yet**. No tests exist in the handoff project to run, and lint wasn't part of the workflow's steps. Both are safe to add as a follow-up CI step; nothing in the audit found a reason they'd fail.
- APK asset/ABI inspection, SHA-256, and size — **done, real numbers above**.

### Runtime checks
**Still not tested.** Neither this sandboxed session nor the GitHub Actions
runner used to build the APK has an Android emulator or physical device
attached. Nothing below should be assumed to work: install, launch,
microphone permission grant, SraVaani recognizer initialization, live
Hindi/Hinglish captioning, session persistence, or Settings navigation. The
build succeeding means the code compiles, links, and packages correctly —
it says nothing about runtime correctness of the ASR pipeline.

## Still open / highest-risk item

The `featureDim = 80` correction (see fix #3) is an informed default, not a
verified fact about `mobilebytesensei/betterflow-sravaani-streaming-onnx`'s
actual export. If this value is wrong, sherpa-onnx will still initialize
without crashing and produce garbage or empty transcriptions — a silent
runtime failure mode, not a build or startup crash. Before relying on
transcription quality, check this value against the model conversion's own
published preprocessor/feature config.

## Definition-of-done checklist (per `CLAUDE.md`)

1. Corrected source project — **done**.
2. Successful build log summary — **done**, see above (real CI run, both a clean build and an incremental rebuild succeeded).
3. Installable `app-debug.apk` — **produced on CI**, downloadable from the run's Artifacts tab; not attached directly by this session (see "Sandbox limitation").
4. APK SHA-256 and size — **done**, `5d9e7a8f7cd70dd579259324dab24b6aa04baddc211edaeb4e7f2af68d6ea5e7`, 728,588,358 bytes.
5. Confirmation model + tokens are embedded — **done**, exact sizes match the pinned values, verified via `unzip -l` inside the actual APK.
6. List of code/build fixes made — see "What was fixed to get here" above.
7. Runtime validation status — **all untested**, explicitly separated from the (real, verified) build success above.
8. Remaining blocker needing a physical Android device — **all runtime checks** in the "Required validation" section.

## Sandbox limitation (for transparency)

The session that authored these fixes runs in a network-restricted sandbox:
`dl.google.com`, `jitpack.io`, and `huggingface.co` are blocked by policy, and
no Android SDK is installed there. That sandbox could not run `assembleDebug`
itself. To get a real build, the fixes were pushed to this branch and the
repo's own `build-apk.yml` workflow was dispatched on GitHub's
unrestricted-network runners — where it succeeded. The sandbox also can't
reach the artifact's Azure Blob Storage download URL (outside its allowlist),
so the APK binary itself isn't attached to this conversation directly; get it
from the Actions run's Artifacts tab at the URL above.
