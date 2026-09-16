# Final APK Handoff Checklist

- [x] Audit project structure and dependencies
- [x] Add/fix Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`)
- [x] Verify sherpa-onnx artifact resolves — confirmed on CI (JitPack resolves `com.github.k2-fsa:sherpa-onnx:v1.13.4` fine on an unrestricted-network runner)
- [x] Verify `SravaaniSttEngine` against exact runtime API — fixed a call to a nonexistent `OnlineStream.setOption` API; feature dimension verified against the model's real ONNX graph shape (128, confirmed via CI, see FINAL_BUILD_REPORT.md)
- [x] Verify SraVaani model download and expected byte sizes — download task hardened (retry/backoff/resume) and confirmed working on CI: both files downloaded and matched their pinned exact sizes
- [x] Build debug APK successfully — **BUILD SUCCESSFUL** on four consecutive GitHub Actions runs, latest [`35156873485`](https://github.com/myworkdesk2112-del/gh-repo-clone-jacobziolkowski-twilio-vision-bot/actions/runs/35156873485)
- [x] Run lint — `:app:lintDebug` added to CI and **passed** ("BUILD SUCCESSFUL", no lint errors); HTML report uploaded as the `lint-report` CI artifact for a manual warnings skim before release
- [x] Run tests where available — added 3 JUnit test classes (`BhashiniSttEngineTest`, `LanguagesTest`, `ModelsTest`); `:app:testDebugUnitTest` **passes** on CI, report uploaded as `unit-test-report`
- [x] Inspect APK: SraVaani ONNX + `tokens.txt` embedded — confirmed via `unzip -l`, exact byte sizes match (658,699,885 / 68,907)
- [x] Inspect APK: arm64 native libs present — confirmed: `libonnxruntime.so`, `libsherpa-onnx-jni.so`, `libsherpa-onnx-c-api.so`, `libsherpa-onnx-cxx-api.so`, `libandroidx.graphics.path.so`
- [x] Compute APK SHA-256 and final size — `5e7d2326e6d6191db325f8db590f301eff603208ee3030bde49ccd7edea78ed8`, 728,589,394 bytes
- [ ] Install/launch/test microphone + live captions if a device/emulator is available — no device/emulator available in either the authoring sandbox or the CI runner used to build; this is the only item that cannot be closed without physical hardware
- [x] Preserve Even Realities visual style — unchanged from handoff, no UI regressions introduced
- [x] Create `FINAL_BUILD_REPORT.md`
- [x] Return final APK — produced and verified on CI; downloadable from the Actions run's Artifacts tab (the authoring sandbox can't reach Azure Blob Storage to attach the binary directly — see FINAL_BUILD_REPORT.md)
