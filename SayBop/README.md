# SayBop — native Android offline musical-word videos

**Tagline:** Type it. Bop it. Share it.

This is the maintained Android project, originally developed under BlazeDitty in the repository history. Its draft pull request remains intact as historical source.

## Build

JDK 17, Android SDK 35, Android Gradle Plugin 8.7.3, Gradle 8.9.

```sh
cd SayBop
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
bash tests/run.sh
```

Build output: `app/build/outputs/apk/debug/app-debug.apk`.

## Operation

Choose a local audio file, installed offline TTS voice, enter a phrase, generate a musical timed sequence, preview, then export MP4 to `Movies/SayBop`. The app has no Internet permission. Voice packs must already be installed. The DSP uses offline granular overlap-add pitch and time shaping; it is not a studio-quality singing synthesizer. Audio processing is capped at 18 seconds in this MVP.

## Verification policy

A workflow passing `assembleDebug` proves an APK was compiled, **not** that it installed, generated TTS on a physical phone, or exported a playable MP4. Emulator/instrumentation and device tests must be reported separately. Codec availability depends on the Android device.

Only use music you have rights to redistribute. No proprietary Ditty code, sounds or assets are bundled.
