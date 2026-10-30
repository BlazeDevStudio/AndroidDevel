#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build-tests
javac -d build-tests app/src/main/java/com/saybop/app/{BeatAnalyzer,WordTimeline,PcmMix,PitchDsp,MelodicVocal,AudioVisuals,SceneDirector,BuiltinVocalSynth,DemoBeat,SongPipeline}.java tests/EngineSmoke.java
java -cp build-tests EngineSmoke
