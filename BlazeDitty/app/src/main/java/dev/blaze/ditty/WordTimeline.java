package dev.blaze.ditty;
import java.util.*;

/** All vocal onsets and visual word animations reference exactly these cue times. */
public final class WordTimeline {
    public static final class Cue {
        public final String text;
        public final int wordIndex, repetition;
        public final double startSeconds, endSeconds;
        public Cue(String word,int index,int repetition,double start,double end) {
            this.text=word; this.wordIndex=index;this.repetition=repetition;
            this.startSeconds=start;this.endSeconds=end;
        }
    }
    private WordTimeline() {}
    public static List<Cue> build(String text,BeatAnalyzer.Grid grid,double clipSeconds) {
        String[] words=Arrays.stream(text.trim().split("\\s+"))
                .filter(w->!w.isEmpty()).toArray(String[]::new);
        if(words.length==0)return Collections.emptyList();
        if(words.length>120)throw new IllegalArgumentException("Prototype supports at most 120 words");
        double beat=grid.beatSeconds();
        double step=Math.max(beat*.5, Math.min(beat*2, (clipSeconds-1.5)/(words.length+1.0)));
        double start=grid.phaseSeconds+beat;
        while(start<0.4)start+=beat;
        List<Cue> cues=new ArrayList<>();
        for(int repetition=0; repetition<3; repetition++) {
            double base=start+repetition*(words.length*step+beat*2);
            for(int i=0;i<words.length;i++) {
                double t=base+i*step;
                if(t+0.1>clipSeconds-.2) return cues;
                cues.add(new Cue(words[i],i,repetition,t,Math.min(t+step*.94,clipSeconds)));
            }
        }
        return cues;
    }
    public static Cue at(List<Cue> cues,double seconds) {
        for(Cue c:cues)if(seconds>=c.startSeconds && seconds<c.endSeconds)return c;
        return null;
    }
}