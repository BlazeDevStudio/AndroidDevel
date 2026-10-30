package com.saybop.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Pure-Java timing, voice-cue and soundtrack composition boundary. */
public final class SongPipeline {
    private SongPipeline() {}

    /** Metadata is immutable; backing PCM is the caller-owned read-only input. */
    public static final class Plan {
        public final BeatAnalyzer.Grid grid;
        public final List<WordTimeline.Cue> cues;
        public final List<String> words;
        public final AudioVisuals visuals;
        private final float[] backing;
        private Plan(float[] music, BeatAnalyzer.Grid beat, List<WordTimeline.Cue> cues,
                     List<String> words, AudioVisuals visuals) {
            this.backing=music;
            this.grid=beat;
            this.cues=Collections.unmodifiableList(new ArrayList<>(cues));
            this.words=Collections.unmodifiableList(new ArrayList<>(words));
            this.visuals=visuals;
        }
        public double durationSeconds() { return backing.length/(2.0*PcmMix.RATE); }
    }

    public static Plan plan(String phrase,float[] stereo,double manualBpm,double offsetMs) {
        if(phrase==null || phrase.trim().isEmpty())
            throw new IllegalArgumentException("Phrase is empty");
        if(stereo==null || stereo.length<2 || stereo.length%2!=0)
            throw new IllegalArgumentException("Invalid stereo backing PCM");
        BeatAnalyzer.Grid beat=BeatAnalyzer.correct(
            BeatAnalyzer.detect(stereo,PcmMix.RATE),manualBpm,offsetMs);
        double seconds=stereo.length/(2.0*PcmMix.RATE);
        List<WordTimeline.Cue> cues=WordTimeline.build(phrase,beat,seconds);
        if(cues.isEmpty())
            throw new IllegalArgumentException("The track is too short for a word animation");
        List<String> words=Arrays.asList(phrase.trim().split("\\s+"));
        return new Plan(stereo,beat,cues,words,AudioVisuals.analyze(stereo,PcmMix.RATE));
    }

    /** Compose exactly one mono word PCM per planned text word, preserving cue times. */
    public static float[] mix(Plan plan,List<float[]> voices) {
        if(plan==null || voices==null || voices.size()!=plan.words.size())
            throw new IllegalArgumentException("Expected one voice recording per word");
        return PcmMix.compose(plan.backing,plan.cues,voices);
    }
}
