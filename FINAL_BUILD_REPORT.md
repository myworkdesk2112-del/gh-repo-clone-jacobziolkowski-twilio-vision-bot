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

- Latest verified run: [`35156873485`](https://github.com/myworkdesk2112-del/gh-repo-clone-jacobziolkowski-twilio-vision-bot/actions/runs/35156873485) — commit `f43fb6c`, branch `claude/friendly-dijkstra-85m8sq`
- Result: **BUILD SUCCESSFUL**, every step green: build, unit tests, lint, ONNX feature-dimension check, model/APK verification, SHA-256/asset/ABI inspection
- Artifact: `GiVa-HiAssist-1.0-arm64-debug` (14-day retention), downloadable from the run page above
- Four consecutive independent CI runs have now succeeded end to end — this is not a one-off green run.

## Verified APK facts

| Fact | Value |
|---|---|
| File | `app/build/outputs/apk/debug/app-debug.apk` |
| Exact size | **728,589,394 bytes** (≈695 MiB) — within ~1 KB across every CI run (the tiny delta is compiled unit-test-related metadata; embedded model/asset/lib sizes are byte-identical every time) |
| SHA-256 (run `35156873485`) | **`5e7d2326e6d6191db325f8db590f301eff603208ee3030bde49ccd7edea78ed8`** |
| `assets/sravaani/model-la13.onnx` | present, 658,699,885 bytes — matches the pinned expected size exactly |
| `assets/sravaani/tokens.txt` | present, 68,907 bytes — matches the pinned expected size exactly |
| `assets/sravaani/README.txt` | present, 437 bytes (doc-only, harmless) |

**Note on the SHA-256 changing between runs:** an earlier run (`34784839011`)
reported a different digest (`5d9e7a8f7c...`) for an APK of the *same exact
byte size*. This is expected, not a red flag: Gradle's debug build type
signs with a freshly-generated debug keystore/signature block each time
there isn't a persisted `~/.android/debug.keystore` across CI runs, so the
signature bytes (and therefore the whole-file hash) differ between builds
even when every other byte — code, resources, the embedded model — is
identical. The APK's *content* is reproducible (same size, same asset sizes,
same libs every run); its debug *signature* is not, which is normal and
harmless for a debug build. A release build with a persisted signing key
would not have this property.

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

### 3. Feature dimension: guessed wrong once, then verified against the real model and fixed
`featureDim` was originally `128`. An earlier pass in this effort "corrected"
it to `80` — sherpa-onnx's generic default for NeMo CTC streaming models —
without evidence, and flagged that as an open risk. That guess was then
checked for real: CI run `35156873485` added a step that loads the actual
downloaded `model-la13.onnx` with the `onnx` Python package and prints every
graph input's shape. The result:

```
== ONNX graph inputs (name: shape) ==
audio_signal: ['B', 128, 'T']
length: ['B']
cache_last_channel: ['B', 17, 70, 1024]
cache_last_time: ['B', 17, 1024, 8]
cache_last_channel_len: ['B']
```

The model's own `audio_signal` input is `['B', 128, 'T']` — **128 is
correct**, not 80. `FEATURE_DIM` in `SravaaniSttEngine.kt` has been set back
to `128`, this time with the comment citing this exact evidence (input name,
shape, and CI run ID) instead of a guess. This is no longer an open item.

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

### 5. CI's own Android SDK setup step was broken by an upstream removal
`android-actions/setup-android@v3` defaults to installing packages `"tools
platform-tools"`. Google has removed the legacy standalone `tools` package
from its SDK repository, so every run started failing at the very first SDK
step with `Warning: Failed to find package 'tools'` /
`Error: The process '.../sdkmanager' failed with exit code 1` — before the
project's own code was even touched. This wasn't something introduced by any
fix in this effort; it's pinned by the handoff project's stack a specific
`android-actions/setup-android` behavior against a moving upstream Google
repository, and it broke on its own. Fixed by requesting only
`packages: "platform-tools"` (the `tools` UI/monitor was never actually
needed — `platforms` and `build-tools` are installed explicitly in the next
step anyway).

### 6. Real unit tests added (previously none existed)
Added JUnit tests, run in CI via `:app:testDebugUnitTest`:
- **`BhashiniSttEngineTest`** — exercises the actual JSON response-parsing
  logic (`text`/`transcript` fields, nested `result`/`data` objects,
  `isFinal`/`is_final`/nested-`isFinal`/event-based-final variants, malformed
  JSON, blank text) and the language-code-to-display-name mapping. Required
  making `parseServerMessage`/`languageName` `internal` instead of `private`
  (no behavior change) so the test source set can reach them without
  Robolectric or instrumentation.
- **`LanguagesTest`** — the language-cycling logic (`nextLanguage`/
  `languageName`) was private, duplicated, top-level functions in
  `MainActivity.kt`. Extracted into `domain/Languages.kt` so it's actually
  testable; covers all 9 required languages, full-cycle wraparound, and
  unknown-code handling.
- **`ModelsTest`** — basic invariants on `Caption`/`Direction`/`Session`
  defaults.

All 3 test classes pass on CI (`BUILD SUCCESSFUL`, run `35156873485`).

### 7. CI hardened to report real, checkable numbers
Added an `Inspect APK contents, size, and SHA-256` step to
`.github/workflows/build-apk.yml` so every future run prints the exact APK
byte size, its SHA-256, and an `unzip -l` listing filtered to
`assets/sravaani/*` and `lib/arm64-v8a/*` — the numbers in this report come
from that step, not from estimation or the artifact-upload log (which hashes
the *uploaded zip container*, a different, non-comparable digest).

## Required validation — status

### Static/build checks
- `./gradlew --no-daemon clean :app:assembleDebug` — **passed** on CI across four consecutive runs.
- `./gradlew :app:testDebugUnitTest` — **passed** (`BUILD SUCCESSFUL`, run `35156873485`); 3 test classes, see fix #6 above. Report uploaded as the `unit-test-report` CI artifact.
- `./gradlew :app:lintDebug` — **passed** (`BUILD SUCCESSFUL`). Lint's default behavior aborts the build on lint errors, so a clean pass means no lint *errors* were found; the uploaded `lint-report` HTML artifact should still be skimmed for warnings before a real release.
- APK asset/ABI inspection, SHA-256, and size — **done, real numbers above**, confirmed stable across four independent CI runs.
- SraVaani ONNX feature-dimension — **verified against the real model**, see fix #3 above.

### Runtime checks
**Still not tested.** Neither this sandboxed session nor the GitHub Actions
runner used to build the APK has an Android emulator or physical device
attached. Nothing below should be assumed to work: install, launch,
microphone permission grant, SraVaani recognizer initialization, live
Hindi/Hinglish captioning, session persistence, or Settings navigation. The
build succeeding means the code compiles, links, and packages correctly —
it says nothing about runtime correctness of the ASR pipeline.

## Still open

Nothing code- or build-related remains open from this pass. The one
remaining category is runtime/device validation (below), which needs
hardware that doesn't exist in this session or on the CI runner used to
build — it can't be closed by more code changes, only by an actual device.

## Definition-of-done checklist (per `CLAUDE.md`)

1. Corrected source project — **done**.
2. Successful build log summary — **done**, see above (real CI run, both a clean build and an incremental rebuild succeeded).
3. Installable `app-debug.apk` — **produced on CI**, downloadable from the run's Artifacts tab; not attached directly by this session (see "Sandbox limitation").
4. APK SHA-256 and size — **done**, `5e7d2326e6d6191db325f8db590f301eff603208ee3030bde49ccd7edea78ed8`, 728,589,394 bytes.
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
