#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build-tests
javac -d build-tests app/src/main/java/dev/blaze/ditty/{BeatAnalyzer,WordTimeline,PcmMix,PitchDsp}.java tests/EngineSmoke.java
java -cp build-tests EngineSmoke