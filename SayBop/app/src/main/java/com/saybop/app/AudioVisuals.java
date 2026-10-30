package com.saybop.app;

/**
 * Immutable offline song envelope for video effects.
 * Values follow the imported backing track, not the rendered TTS,
 * so visuals respond even between words and do not depend on artwork files.
 */
public final class AudioVisuals {
    private final float[] loudness,bass,attack;
    private final double framesPerSecond;
    private AudioVisuals(float[] l,float[] b,float[] a,double fps) {
        loudness=l;bass=b;attack=a;framesPerSecond=fps;
    }
    public static AudioVisuals analyze(float[] stereo,int sampleRate) {
        if(stereo==null||stereo.length==0||stereo.length%2!=0||sampleRate<8000||sampleRate>192000)
            throw new IllegalArgumentException("Expected nonempty stereo PCM and valid sample rate");
        int hop=Math.max(1,sampleRate/50);
        int samples=stereo.length/2;
        int count=Math.max(1,Math.min(6000,(samples+hop-1)/hop));
        float[] energy=new float[count],lowBand=new float[count],transientBand=new float[count];
        double lpf=0, alpha=Math.min(.95,2*Math.PI*145/sampleRate);
        float maxEnergy=0,maxBass=0,maxTransient=0,prev=0,falloff=0;
        for(int bin=0;bin<count;bin++) {
            int a=bin*hop,b=Math.min(samples,a+hop);
            double sq=0,lo=0;
            for(int i=a;i<b;i++) {
                float x=(stereo[i*2]+stereo[i*2+1])*.5f;
                if(!Float.isFinite(x))throw new IllegalArgumentException("PCM contains NaN/Infinity");
                lpf+=alpha*(x-lpf);
                sq+=x*x;lo+=lpf*lpf;
            }
            int n=Math.max(1,b-a);
            float e=(float)Math.sqrt(sq/n);
            float bass=(float)Math.sqrt(lo/n);
            float rise=Math.max(0,e-prev);
            falloff=Math.max(rise,falloff*.83f);
            energy[bin]=e;lowBand[bin]=bass;transientBand[bin]=falloff;
            maxEnergy=Math.max(maxEnergy,e);maxBass=Math.max(maxBass,bass);
            maxTransient=Math.max(maxTransient,falloff);prev=e;
        }
        for(int i=0;i<count;i++) {
            energy[i]=Math.min(1,energy[i]/Math.max(.02f,maxEnergy));
            lowBand[i]=Math.min(1,lowBand[i]/Math.max(.02f,maxBass));
            transientBand[i]=Math.min(1,transientBand[i]/Math.max(.04f,maxTransient));
        }
        return new AudioVisuals(energy,lowBand,transientBand,sampleRate/(double)hop);
    }
    private float sample(float[] a,double seconds) {
        if(!Double.isFinite(seconds)||seconds<0)return 0;
        double pos=seconds*framesPerSecond;
        if(pos>=a.length)return 0;
        int at=(int)pos;
        int next=Math.min(a.length-1,at+1);
        float fraction=(float)(pos-at);
        return a[at]*(1-fraction)+a[next]*fraction;
    }
    public float loudness(double seconds){return sample(loudness,seconds);}
    public float bass(double seconds){return sample(bass,seconds);}
    public float attack(double seconds){return sample(attack,seconds);}
    public int sampleCount(){return loudness.length;}
}
