package com.saybop.app;

/** Offline granular overlap-add speech shaping at 44.1kHz. Does not call network services. */
public final class PitchDsp {
    private PitchDsp() {}
    private static float sample(float[] src, double pos) {
        int a=Math.max(0,Math.min(src.length-1,(int)pos));
        int b=Math.min(src.length-1,a+1);
        return (float)(src[a]+(src[b]-src[a])*(pos-a));
    }
    /** Change duration with approximately preserved pitch via Hann-window overlap-add. */
    public static float[] stretch(float[] input, double ratio) {
        if(input==null || input.length==0)return new float[0];
        if(!Double.isFinite(ratio) || ratio<=0 || ratio>16)throw new IllegalArgumentException("Invalid time ratio");
        int length=Math.max(1,(int)Math.round(input.length*ratio));
        if(input.length<512){
            float[] out=new float[length];
            for(int i=0;i<length;i++)out[i]=sample(input,Math.min(input.length-1,i/ratio));
            return out;
        }
        final int window=1024, hop=256;
        int hopOut=Math.max(1,(int)Math.round(hop*ratio));
        float[] output=new float[length+window+hopOut];
        float[] weights=new float[output.length];
        for(int in=0,out=0;in<input.length && out<output.length;in+=hop,out+=hopOut){
            int amount=Math.min(window,input.length-in);
            for(int j=0;j<amount && out+j<output.length;j++){
                double taper=.5-.5*Math.cos(2*Math.PI*j/(window-1));
                output[out+j]+=input[in+j]*(float)taper;
                weights[out+j]+=(float)taper;
            }
        }
        float[] result=new float[length];
        for(int i=0;i<length;i++)result[i]=weights[i]>1e-6?output[i]/weights[i]:0;
        return result;
    }
    /** Resample after granular stretching to shift actual PCM pitch, then fit a beat cue. */
    public static float[] render(float[] source, int targetSamples, double semitones) {
        if(source==null || source.length==0 || targetSamples<1)throw new IllegalArgumentException("Empty voice/cue");
        if(targetSamples>44100*60)throw new IllegalArgumentException("Vocal cue too long");
        double factor=Math.pow(2.0,Math.max(-7,Math.min(7,semitones))/12.0);
        float[] extended=stretch(source,factor);
        float[] pitched=new float[source.length];
        for(int i=0;i<pitched.length;i++)
            pitched[i]=sample(extended,Math.min(extended.length-1,i*factor));
        return stretch(pitched,targetSamples/(double)pitched.length);
    }
}
