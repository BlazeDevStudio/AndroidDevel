# SayBop / legacy Ditty 2.9.83.1201 — clean-room APK reconciliation

Source: user-provided original APK `Ditty_2.9.83.1201_APKPure.apk`.
SHA-256: `1d8df82f92e199d2453a57621055e0ab5d61af12f586afdb0281a1b0c4ae4c28`
Original APK: 85,201,315 bytes (80.9 MiB), 2,387 ZIP entries, 101.05 MiB uncompressed.
The original APK is reference evidence only and **is not redistributed, committed, modified, or copied**.

## Observed architecture
- Four Android DEX files, Xamarin/Mono runtime libraries for ARMv7/ARM64, roughly 135 managed .NET DLL assemblies.
- Main managed application `assemblies/Ditty.Droid.dll` (2,241,024 bytes).
- Names visible in managed metadata include `MuxService`, `GetSongTimingAsync`, `InsertSynthVocalText`, `RemoveSynthVocalTrack`, `PrepareSong`, `SongPlayer`, `OpenGLViewModel`. These **suggest** a separate song/timing/vocal pipeline and remote services; no claim of exact code paths or server-side synthesis without execution traces.
- `assets/Video/`: approximately 192 files, 8.61 MiB expanded; 28 SDF font atlases (.fnt + PNG), about 35 color LUT images, vertex/fragment shader files and 51 OBJ meshes. The moodDefs configuration names many word/background animation strategies.
- No bundled standalone audio tracks (.mp3, .wav, .ogg, .m4a, .aac) were found by APK entry extension.
- Library sizes are not a fair proxy for processing quality: original includes large framework, messaging, database, social and commercial service dependencies.

## SayBop gaps / parity requirements
1. **Central feature (NO-GO):** current system-TTS per-word source and rudimentary pitch shaping are not proven equivalent to melodic singing or Ditty's vocal-processing pipeline. Need an original real offline singing synthesis approach, phoneme/syllable timing and objective perceptual tests.
2. **Temporal accuracy:** manual BPM/phase correction and strictly quantized beat-grid cue timing implemented in this iteration; still lacks tempo-change mapping and phrase recognition.
3. **Typography:** first clean-room selectable Pop/Slide/Pulse variants implemented, not the original's large effect system. No proprietary assets copied.
4. **Export:** real H.264/AAC export requires emulator or physical playback validation. Existing Android 15 emulator CI failures were from /dev/kvm permissions AND an erroneous multi-line runner script (`set -o pipefail` invoked through `sh`). Fixed by a checked-in single bash script and KVM permissions.
5. **Offline-only guarantee:** original's external services are not a requirement for SayBop. Offline Android voice-pack availability and end-to-end airplane mode are still unverified.
6. **Limitations:** 18s audio cap; no production-quality music processing; lifecycle cancellation, robust voice selection and diverse visuals are incomplete.

## Engineering rules
- Treat Ditty as a functional/UX reference only; do not extract proprietary managed code, bundled fonts, effects, graphics, or copyrighted music for shipping.
- Reimplement using original code and dependencies with verified redistribution terms.
- A green assemble/lint workflow is not end-to-end functionality proof.
- No release, merge, or functional/quality claims until signed-off offline end-to-end tests.
