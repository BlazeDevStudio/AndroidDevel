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

Choose a local audio file (or built-in original demo beat), use the bundled robotic synth or an installed offline Android TTS voice, enter a phrase, generate a musical timed sequence, preview, then export MP4 to `Movies/SayBop`. The app has no Internet permission. The built-in formant/grapheme synth needs no installed voice pack; its English-ish vocal sound is intentionally robotic and is **not intelligible TTS**. A proper installed system TTS voice is still preferable for understandable spoken lyrics. The DSP uses offline granular overlap-add pitch and time shaping; it is not a studio-quality singing synthesizer. Audio processing is capped at 18 seconds in this MVP.

## Verification policy

A workflow passing `assembleDebug` proves an APK was compiled, **not** that it installed, generated TTS on a physical phone, or exported a playable MP4. Emulator/instrumentation and device tests must be reported separately. Codec availability depends on the Android device.

Only use music you have rights to redistribute. No proprietary Ditty code, sounds or assets are bundled.

## Auto Director scenes (this preview)

The sixth animation choice, **Auto Director**, cycles six procedural native scenes
(on each four-beat section): oscillating wave ribbons, light trails, radial fan,
sliding panel composition, prism diamonds and beat rings. It deliberately removes
the repeated software slogan from video frames. The original music track's RMS,
bass envelope and attacks modulate colors, geometry, movement and beat flashes;
the typed words remain timed to the same musical grid used by the vocal mix.

The Android preview and H.264/AAC export use one deterministic SceneDirector,
one immutable music envelope and the same renderer. At 120 BPM scenes switch
every two seconds. No Ditty artwork or proprietary audio assets are copied.
Real on-device offline voice availability and singing naturalness remain separate
validation gates.

## Offline first-run path

Choose **USE ORIGINAL DEMO BEAT (120 BPM)**, keep **Built-in Synth** selected, enter an English phrase, choose an animation, then generate. This exercises real local PCM synthesis (vowel formants, noise consonants), the beat-quantized word mixer, a 12-second generated stereo backing track and MP4 exporter without downloads or Android speech voice packs. Android TTS voices appear as optional additional choices if present. Built-in synth is an audible robotic sketch, not a genuine high-quality speech/singing voice; pronunciation and multilingual input are limited. Android emulator instrumentation exercises built-in vocal samples and encoded video.

## UI first-run regression

Android instrumentation now launches MainActivity, selects the original demo beat, triggers the built-in synthesis path, verifies generation reaches Ready, and verifies that both preview playback and video export are enabled. This is separate from the 3-second synthetic-song MP4 codec validation. The UI run does not pretend to listen to or rate vocal naturalness.
