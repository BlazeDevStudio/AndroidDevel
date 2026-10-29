package dev.blaze.ditty;

/** Offline spectral-free onset-envelope autocorrelation. No network or microphone access. */
public final class BeatAnalyzer {
    public static final class Grid {
        public final double bpm;
        public final double phaseSeconds;
        public Grid(double bpm, double phaseSeconds) { this.bpm=bpm; this.phaseSeconds=phaseSeconds; }
        public double beatSeconds() { return 60.0 / bpm; }
        public double nearestBeat(double seconds) {
            return phaseSeconds + Math.round((seconds-phaseSeconds) / beatSeconds()) * beatSeconds();
        }
    }
    private BeatAnalyzer() {}
    public static Grid detect(float[] stereo, int sampleRate) {
        if (stereo == null || stereo.length < sampleRate) return new Grid(120, 0);
        int hop = Math.max(1, sampleRate/100); // 10 ms
        int frames = Math.min(stereo.length/2/hop, 3000);
        if (frames < 100) return new Grid(120,0);
        double[] e = new double[frames];
        for (int i=0;i<frames;i++) {
            double rms=0;
            int st=i*hop*2, lim=Math.min(st+hop*2, stereo.length);
            for (int k=st;k<lim;k+=2) { double s=(stereo[k]+stereo[k+1])*.5; rms+=s*s; }
            e[i]=Math.sqrt(rms/Math.max(1,(lim-st)/2));
        }
        double[] onset = new double[frames];
        for (int i=1;i<frames;i++) onset[i]=Math.max(0,e[i]-e[i-1]);
        double best=-1; int bestLag=50;
        for (int lag=33;lag<=100;lag++) { // 60 - 180 BPM
            double score=0;
            for (int i=lag;i<frames;i++) score+=onset[i]*onset[i-lag];
            // Slight preference toward stable, broad-pop tempo to reduce half-time ambiguity.
            score *= 1.0 - 0.0004*Math.abs(lag-50);
            if(score>best){best=score;bestLag=lag;}
        }
        double bpm=6000.0/bestLag;
        double[] phaseScore = new double[bestLag];
        for(int i=0;i<frames;i++) phaseScore[i%bestLag]+=onset[i];
        int phase=0;for(int i=1;i<bestLag;i++)if(phaseScore[i]>phaseScore[phase])phase=i;
        return new Grid(bpm,phase*.01);
    }
}