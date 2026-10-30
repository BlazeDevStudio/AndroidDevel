package com.saybop.app;

/** Original generated 120 BPM, 12-second demo backing track; no copyrighted samples. */
public final class DemoBeat {
    private DemoBeat(){}
    public static final int RATE=44100;
    public static float[] create() {
        int count=RATE*12;
        float[] music=new float[count*2];
        // Bass root C2, fifth G2, fourth F2, with a quiet sustained chord.
        final double[] notes={65.406,98.0,87.307,98.0};
        for(int i=0;i<count;i++){
            double t=i/(double)RATE;
            int bar=(int)(t/2);
            double freq=notes[bar%notes.length];
            double bass=.12*Math.sin(2*Math.PI*freq*t)+.055*Math.sin(2*Math.PI*freq*2*t);
            double chord=.04*Math.sin(2*Math.PI*261.626*t)
                +.033*Math.sin(2*Math.PI*329.628*t)+.027*Math.sin(2*Math.PI*391.995*t);
            float value=(float)(bass+chord);
            music[i*2]=value;
            music[i*2+1]=value;
        }
        int seed=0x53ab004;
        for(int step=0;step<48;step++){
            int start=step*RATE/4;
            boolean kick=step%2==0;
            boolean snare=step%4==2;
            for(int j=0;j<RATE/6 && start+j<count;j++){
                double t=j/(double)RATE;
                seed=seed*1664525+1013904223;
                double white=((seed>>>8)/(double)0xFFFFFF)*2-1;
                double hat=.11*white*Math.exp(-t*95);
                double drum=hat;
                if(kick)drum+=.43*Math.sin(2*Math.PI*(58*t+4.2*(1-Math.exp(-t*19))))*Math.exp(-t*23);
                if(snare)drum+=.19*white*Math.exp(-t*27);
                int pos=(start+j)*2;
                music[pos]+=(float)drum;
                music[pos+1]+=(float)(drum*(step%2==0?.95:1.05));
            }
        }
        for(int i=0;i<music.length;i++)music[i]=(float)Math.tanh(music[i]*.85);
        return music;
    }
}
