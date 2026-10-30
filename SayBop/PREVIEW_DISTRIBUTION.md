# Preview distribution policy

Every successful **push** to `feat/saybop-android-mvp-20261030` automatically creates/updates a SHA-pinned **GitHub prerelease** named `saybop-preview-<12-character commit>` with the actual compiled debug APK, source ZIP, SHA256SUMS.txt, and BUILD.txt. Links: https://github.com/BlazeDevStudio/AndroidDevel/releases

This previews code only. The APK build job gates publication on Java DSP smoke, Gradle lint and assemble. Android emulator/media tests run separately and are **not** prerequisites of the prerelease; they must never be represented as passing without their independent run. On builds that fail, no prerelease is published. PR runs cannot publish.

Prior preview releases remain accessible; the tag must match the source commit. Never publish the original proprietary Ditty APK/assets. The optional private Google Drive archive remains a separate backup destination, not a publishing dependency.

## Actual media pipeline

Typed text → installed Android offline TTS word WAVs → syllable-shaped DSP → beat-quantized vocal cue times → background/voice mix with limiter.

Imported MP3/WAV/M4A → decoded music PCM → BPM and phase detection/override → 50 Hz immutable AudioVisuals envelope (RMS, lowpass bass, positive attack) → procedural native-reactive backgrounds, waveform bars, beat rings, kinetic typography, preview and H.264/AAC MP4.

Video renderers use the SAME `AudioVisuals` object calculated from the original imported song as the live audio preview; they do not react to arbitrary wall-clock time only. An emulator test checks synthetic musical PCM and actual encoded MP4. Musical singing quality/phoneme alignment is still not proven.

Silence has a zero envelope; no third-party copyrighted artwork or music is bundled.
