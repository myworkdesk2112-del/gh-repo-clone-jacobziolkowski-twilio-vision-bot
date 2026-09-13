# Final APK Handoff Checklist

- [x] Audit project structure and dependencies
- [x] Add/fix Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`)
- [ ] Verify sherpa-onnx artifact resolves — blocked, jitpack.io unreachable in this environment
- [x] Verify `SravaaniSttEngine` against exact runtime API — fixed a call to a nonexistent `OnlineStream.setOption` API
- [x] Verify SraVaani model download and expected byte sizes — download task hardened (retry/backoff/resume); actual download blocked, huggingface.co unreachable in this environment
- [ ] Build debug APK successfully — blocked, see FINAL_BUILD_REPORT.md
- [ ] Run lint — blocked (requires the Android Gradle Plugin, which cannot be resolved here)
- [ ] Run tests where available — blocked, same reason
- [ ] Inspect APK: SraVaani ONNX + `tokens.txt` embedded — no APK was produced
- [ ] Inspect APK: arm64 native libs present — no APK was produced
- [ ] Compute APK SHA-256 and final size — no APK was produced
- [ ] Install/launch/test microphone + live captions if a device/emulator is available — no device/emulator in this environment
- [x] Preserve Even Realities visual style — unchanged from handoff, no UI regressions introduced
- [x] Create `FINAL_BUILD_REPORT.md`
- [ ] Return final APK — not possible from this environment; see FINAL_BUILD_REPORT.md for exact blockers and what a machine with normal internet + Android SDK needs to do to finish the build
