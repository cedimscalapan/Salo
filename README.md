# SALO — Native Android Kotlin App (v1)

**SALO is an Android app, not an HTML/WebView wrapper.** All interface controls, offline storage, planning rules and command interpretation are written in Kotlin using the built-in Android SDK.

## What it does

- El Niño and La Niña **example scenarios**; user selects the mode, not an inferred ENSO alert.
- Choose sample Oriental Mindoro municipality: Bulalacao, Naujan, Baco, Puerto Galera.
- El Niño: supply reserve days from stored water and daily demand.
- La Niña: illustrative roof runoff captured, limited by available tank space, and drainage follow-up tasks.
- One button produces a three-task prioritized action plan; track completed items and designated roles.
- Reports can be shared through Android share sheet as plain text.
- Data persists on the device through Android SharedPreferences, without an internet permission.
- Offline typed Filipino/English/Taglish commands: rule-based phrase matching, review before applying changes.
- Microphone button: **only** Android 12+ devices that report an available on-device recognizer. No cloud fallback. Filipino/Taglish speech accuracy is NOT guaranteed and model availability varies by device. Text entry works regardless.
- Minimal Home / My plan / Report navigation.

## Build an APK (Android Studio)

1. Install Android Studio and Android SDK Platform 35 / Build Tools 35.
2. Extract this source ZIP, then use **File → Open** to open the `SALO_Kotlin_Android` folder.
3. Let Android Studio install Gradle 8.11.1 and dependencies (internet required the first time). If prompted, use Gradle 8.11.1 and JDK 17.
4. Run `:app:assembleDebug` from the Gradle tool window, or use **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
5. APK path: `app/build/outputs/apk/debug/app-debug.apk`. This is an automatically debug-signed sideloadable APK, if the build succeeds.
6. On the Android phone, copy the APK, open it, and approve installation from that source when prompted.

CLI, after installing SDK & Gradle:

```bash
gradle :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

## Build through GitHub Actions

1. Open [cedimscalapan/Salo Actions](https://github.com/cedimscalapan/Salo/actions/workflows/build-apk.yml).
2. Choose **Build SALO Android APK** → **Run workflow**, then select `main`. Pushing changes to `main` also starts a build.
3. Once the run **succeeds**, open the run, scroll to **Artifacts**, and download `SALO-Android-APK`.
4. Extract `SALO-debug.apk` from the downloaded ZIP and install on an Android phone. The APK is debug-signed for sideloading, not a Play Store release.
5. Optionally, create a `v*` tag to publish `SALO-debug.apk` on the repository's Releases page.

The workflow uses a standard Android SDK/Gradle CI environment and runs JUnit unit tests before producing the APK. This package was **not** built or installed in the authoring environment because no Android SDK/Gradle was available there. A real successful build and device test are required before claiming production readiness.

## Test on phone

1. Enable airplane mode and start the app. Change scenarios and municipality, enter reserve figures, and generate a plan.
2. Tap a task complete. Close and reopen the app. Verify the saved values and task state remain.
3. Type: `El Niño sa Bulalacao. May 8000 liters. Daily demand 2500 liters. Gumawa ng plano` and confirm the preview says 8,000 L, 2,500 L/day and **3.2 days**.
4. Change to La Niña and use `La Niña sa Naujan, rainfall 120 mm, barado ang kanal`. Ensure a review dialog appears and the drainage task is prioritized after approval.
5. Share the report to an installed text-sharing app and inspect the content.
6. If Android reports on-device recognition available, test microphone offline separately. If recognition fails or language support is missing, use typed commands.

## Technical limitations / transparency

- The values for municipalities are invented demo data. The user-selected mode is an exercise, not a statement about the actual local climate.
- Not a flood prediction app, not a PAGASA alert receiver, not a water-quality analysis, and not a tool to ration drinking water.
- Seven-day water planning horizon is an illustrative comparison, **not** an official emergency threshold.
- Rainwater capture estimate: `roof_area_m² × scenario_rainfall_mm × 0.75`, capped at `(tank_capacity_L − stored_water_L)`.
- Voice recognition uses Android's **on-device recognizer when available**, not an included trained multilingual model. Typed language parsing is deterministic—not an LLM.
- Task completion is self-reported.
- Use expert-reviewed inputs and local DRRM coordination before any real-world deployment.

## App stack

- Native Kotlin Android Activity + Android Views (no WebView, no heavy dependencies)
- Local `SharedPreferences` + `org.json`
- Android `SpeechRecognizer.createOnDeviceSpeechRecognizer`, API 31+, conditional
- Android Gradle Plugin 8.9.2, Gradle 8.11.1, Kotlin 2.0.21, compileSdk 35, minSdk 26
- Unit tests in `app/src/test` and pure Kotlin CLI smoke tests in `tests/`
