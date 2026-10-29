package com.saybop.app;

/**
 * Deterministic, local, *approximate* syllable-shaped speech melody.
 * Does not infer phoneme boundaries from TTS PCM, synthesize a singing voice,
 * or claim parity with Ditty's engine.
 */
public final class MelodicVocal {
    private static final int RATE=44100;
    private static final int[] SCALE={0,2,4,7,9,7,4,2};
    private MelodicVocal() {}

    /** Orthographic vowel-group heuristic; not a language-agnostic phoneme analyzer. */
    public static int estimateSyllables(String word) {
        if(word==null || word.isEmpty())return 1;
        String s=word.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z]","");
        if(s.isEmpty())return 1;
        int syllables=0;
        boolean prior=false;
        for(int i=0;i<s.length();i++) {
            char c=s.charAt(i);
            boolean vowel=("aeiou".indexOf(c)>=0)||
                (c=='y' && i>0 && "aeiou".indexOf(s.charAt(i-1))<0);
            if(vowel && !prior)syllables++;
            prior=vowel;
        }
        if(s.endsWith("e") && !s.endsWith("le") && syllables>1)syllables--;
        return Math.max(1,Math.min(4,syllables));
    }

    /**
     * Split an already synthesized word into bounded sections and give each
     * section a reproducible note, smoothing joins in the time domain.
     * No note boundary relies on an unverified phoneme timestamp.
     */
    public static float[] render(float[] source,int targetSamples,
                                 String word,int wordIndex,int repetition) {
        if(source==null || source.length==0 || targetSamples<1)
            throw new IllegalArgumentException("Empty voice/cue");
        if(targetSamples>RATE*60)throw new IllegalArgumentException("Cue exceeds 60 seconds");
        int syllables=estimateSyllables(word);
        int segments=Math.max(syllables,(targetSamples>=RATE/3 && source.length>=RATE/8)?2:1);
        segments=Math.max(1,Math.min(4,Math.min(segments,Math.min(source.length/512,targetSamples/512))));
        if(segments==0)segments=1;
        float[] output=new float[targetSamples],weight=new float[targetSamples];
        int seam=Math.min(RATE/80,Math.max(0,targetSamples/(segments*8)));
        for(int part=0;part<segments;part++){
            int srcFrom=(int)((long)source.length*part/segments);
            int srcTo=(int)((long)source.length*(part+1)/segments);
            int dstFrom=(int)((long)targetSamples*part/segments);
            int dstTo=(int)((long)targetSamples*(part+1)/segments);
            if(srcTo<=srcFrom || dstTo<=dstFrom)continue;
            int start=Math.max(0,dstFrom-(part>0?seam:0));
            int end=Math.min(targetSamples,dstTo+(part<segments-1?seam:0));
            int sourcePad=Math.min(source.length/16,seam);
            int from=Math.max(0,srcFrom-(part>0?sourcePad:0));
            int to=Math.min(source.length,srcTo+(part<segments-1?sourcePad:0));
            float[] sample=java.util.Arrays.copyOfRange(source,from,to);
            int note=SCALE[Math.floorMod(wordIndex+repetition*2+part, SCALE.length)];
            int root=SCALE[Math.floorMod(wordIndex+repetition*2,SCALE.length)];
            double semitones=note-root+(root-2);
            float[] pitched=PitchDsp.render(sample,end-start,semitones);
            for(int j=0;j<pitched.length;j++){
                int dst=start+j;
                double fadeIn=part>0?Math.min(1.0,(dst-start+1.0)/(seam*2.0+1)):1;
                double fadeOut=part<segments-1?Math.min(1.0,(end-dst)/(seam*2.0+1)):1;
                float w=(float)Math.max(0,Math.min(fadeIn,fadeOut));
                output[dst]+=pitched[j]*w;
                weight[dst]+=w;
            }
        }
        for(int i=0;i<output.length;i++) {
            output[i]=weight[i]>1.e-6f?output[i]/weight[i]:0;
            if(!Float.isFinite(output[i]))throw new IllegalStateException("Invalid vocal sample");
        }
        return output;
    }
}
