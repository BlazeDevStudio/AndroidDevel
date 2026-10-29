# Blaze Ditty · Native Android text-to-song video prototype

A **real native Android source project** targeting Android 10+ (API 29+). It never calls a server, uses no account, asks for no Internet permission, and selects an *already installed offline Android TextToSpeech voice*.

## What this prototype actually implements

- Select a locally stored audio track using Android's Storage Access Framework.
- Decode the audio fully offline (MediaExtractor and MediaCodec, to stereo 44.1 kHz PCM).
- Estimate tempo and beat phase using an onset-energy autocorrelation envelope, restricted to roughly 60–180 BPM.
- Select installed Android TTS packs filtered by `!voice.isNetworkConnectionRequired()`.
- Synthesize each word via the selected **system TTS engine**, with a basic ascending/descending pitch pattern.
- Place each generated word on a shared beat-derived timestamp timeline.
- Match each word's duration to its cue using linear resampling (rough, not high-fidelity time stretching).
- Duck backing music under each word and mix stereo PCM.
- Preview with a locally generated WAV and kinetic text driven from the *same cue timeline* and media player's position.
- Export portrait 480×848 H.264/AAC MP4 through Android MediaCodec + MediaMuxer into Movies/BlazeDitty.
- No permissions for microphone, network, or broad storage access.

## Important honesty about Ditty fidelity

Ditty by Zya was a proprietary **text-to-singing** app. Merely choosing a TTS engine cannot perfectly reproduce its expressive melodies. This v0.1 is a substantial original offline engineering prototype, **not a verified finished clone**. The first version maps speech per word to approximate musical notes. It does not yet implement formant-preserving musical pitch correction, multi-syllable note segmentation, a true tempo-map for changing-BPM songs, or exactly reproduce Ditty's effects and templates.

The beat analyzer can misread swing, drums, silence, and halftime tempo. The MP4 encoder uses Android's YUV420 flexible input API, which varies by hardware. On devices lacking a compatible H.264 encoder, export will fail with a diagnostic rather than silently upload data. Some installed TTS voices are reported offline yet require their voice data to be downloaded once in Android settings.

## How to build

1. Open this folder in Android Studio (current version with Android Gradle Plugin 8.7.3 support).
2. Install Android SDK Platform 35 and JDK 17. Sync Gradle. If your environment requires a Gradle wrapper, generate one with Gradle 8.9+; wrapper binaries are not included in this source ZIP.
3. Build and run `app` on an Android 10+ physical device. Select a local song (prefer 15+ seconds), select a listed offline voice, enter text, Generate, Play, Export.
4. Find the exported MP4 in `Movies/BlazeDitty`.

## Verification status

The sandbox used to write this source **does not have Android SDK, Gradle, emulator, or physical-device access**. Therefore the Android APK has **not been compiled or executed** here. The pure-Java beat/timing/mix components have a standalone regression test in `tests/EngineSmoke.java` runnable via `tests/run.sh`. Real-device checks, H.264 encoder format negotiation and accurate melodic vocal quality remain open.

The source intentionally does not embed copyrighted songs, proprietary Ditty code, or third-party assets. Users are responsible for their own rights to distribute derivative mixes from their imported music.