package com.saybop.app;
import java.util.*;

/** Offline voice/music mix with aligned cue boundaries, attenuation and peak limiter. */
public final class PcmMix {
    public static final int RATE=44100;
    private PcmMix() {}
    public static float[] compose(float[] background, List<WordTimeline.Cue> cues, List<float[]> voiceWavs) {
        if(background==null||background.length==0)return new float[0];
        float[] result=background.clone();
        // music ducking with tapered ingress/egress around each scheduled word
        for(WordTimeline.Cue cue:cues) {
            int a=Math.max(0,(int)((cue.startSeconds-.08)*RATE));
            int b=Math.min(result.length/2,(int)((cue.endSeconds+.15)*RATE));
            for(int s=a;s<b;s++) {
                double t=s/(double)RATE;
                double in=Math.max(0,Math.min(1,(t-(cue.startSeconds-.08))/.08));
                double out=Math.max(0,Math.min(1,(cue.endSeconds+.15-t)/.15));
                double factor=1-.45*Math.min(in,out);
                result[s*2]*=factor;result[s*2+1]*=factor;
            }
        }
        for(WordTimeline.Cue cue:cues) {
            if(cue.wordIndex<0 || cue.wordIndex>=voiceWavs.size())throw new IllegalArgumentException("Missing vocal event");
            float[] source=voiceWavs.get(cue.wordIndex); // mono 44.1kHz
            if(source==null||source.length==0)continue;
            int start=(int)Math.round(cue.startSeconds*RATE);
            int target=Math.max(1,(int)((cue.endSeconds-cue.startSeconds)*RATE));
            if(start<0 || start>=result.length/2)continue;
            float[] musical=PitchDsp.render(source,target,new int[]{0,2,4,7,4,2,0,-2}[(cue.wordIndex+cue.repetition*2)%8]);
            for(int j=0;j<target && start+j<result.length/2;j++) {
                // resample each word so it precisely fits its allotted beat time
                float v=musical[j];
                double fade=Math.min(1,Math.min(j/(RATE*.012), (target-j)/(RATE*.025)));
                float add=(float)(v*fade*.82);
                result[2*(start+j)]+=add;
                result[2*(start+j)+1]+=add;
            }
        }
        for(int i=0;i<result.length;i++) result[i]=(float)Math.tanh(result[i]*1.05);
        return result;
    }
    public static short pcm16(float f) {return (short)Math.max(-32768,Math.min(32767,Math.round(f*32767)));}
}