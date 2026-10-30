# SayBop architecture

| Component | Responsibility |
|---|---|
| `MainActivity` | Android UI, storage selection, optional TTS engine lifecycle, playback/export dispatch |
| `SystemTtsSynth` | Android TTS WAV generation, asynchronous utterance completion, timeout and temporary file cleanup |
| `SongPipeline` | Android-free beat, cue, word and reactive-envelope plan plus stereo composition |
| `KineticPreview` | Preview rendering View, receives clock from activity |
| `AudioIO` | Android input decoding, output WAV |
| `BuiltinVocalSynth`, `MelodicVocal`, `PitchDsp`, `PcmMix` | Voice creation / vocal shaping / stereo mix |
| `BeatAnalyzer`, `WordTimeline`, `AudioVisuals` | Analysis and timing |
| `VideoRenderer`, `SceneDirector`, `SurfaceFrames`, `Mp4Exporter` | Procedural visuals and H.264/AAC delivery |
| `DemoBeat` | Original, offline demo music |

## Refactor contract

The existing synthesis and rendering algorithms are unchanged.
`SongPipeline.mix` delegates to the same `PcmMix.compose` API, with the
same beat-cued vocals, and its pure-Java test verifies sample-for-sample
identical output. The original Android first-run and MP4 tests are retained.

No production merge, permission expansion, bundled proprietary Ditty assets,
or real-device acceptance is implied by a green build.

The activity still owns starting/stopping the Android TTS service, while `SystemTtsSynth` exclusively manages per-word asynchronous requests and WAV decoding. This extraction preserves the existing voice selection and 25-second per-word timeout.
