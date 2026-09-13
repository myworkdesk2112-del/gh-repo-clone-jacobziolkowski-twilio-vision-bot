# Final APK Handoff Checklist

- [x] Audit project structure and dependencies
- [x] Add/fix Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`)
- [x] Verify sherpa-onnx artifact resolves — confirmed on CI (JitPack resolves `com.github.k2-fsa:sherpa-onnx:v1.13.4` fine on an unrestricted-network runner)
- [x] Verify `SravaaniSttEngine` against exact runtime API — fixed a call to a nonexistent `OnlineStream.setOption` API
- [x] Verify SraVaani model download and expected byte sizes — download task hardened (retry/backoff/resume) and confirmed working on CI: both files downloaded and matched their pinned exact sizes
- [x] Build debug APK successfully — **BUILD SUCCESSFUL** on three consecutive GitHub Actions runs, latest [`34787263485`](https://github.com/myworkdesk2112-del/gh-repo-clone-jacobziolkowski-twilio-vision-bot/actions/runs/34787263485)
- [x] Run lint — `:app:lintDebug` added to CI and **passed** ("BUILD SUCCESSFUL", no lint errors); HTML report uploaded as the `lint-report` CI artifact for a manual warnings skim before release
- [ ] Run tests where available — no tests exist in the handoff project
- [x] Inspect APK: SraVaani ONNX + `tokens.txt` embedded — confirmed via `unzip -l`, exact byte sizes match (658,699,885 / 68,907)
- [x] Inspect APK: arm64 native libs present — confirmed: `libonnxruntime.so`, `libsherpa-onnx-jni.so`, `libsherpa-onnx-c-api.so`, `libsherpa-onnx-cxx-api.so`, `libandroidx.graphics.path.so`
- [x] Compute APK SHA-256 and final size — `5d9e7a8f7cd70dd579259324dab24b6aa04baddc211edaeb4e7f2af68d6ea5e7`, 728,588,358 bytes
- [ ] Install/launch/test microphone + live captions if a device/emulator is available — no device/emulator available in either the authoring sandbox or the CI runner used to build
- [x] Preserve Even Realities visual style — unchanged from handoff, no UI regressions introduced
- [x] Create `FINAL_BUILD_REPORT.md`
- [x] Return final APK — produced and verified on CI; downloadable from the Actions run's Artifacts tab (the authoring sandbox can't reach Azure Blob Storage to attach the binary directly — see FINAL_BUILD_REPORT.md)
