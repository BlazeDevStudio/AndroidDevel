package com.saybop.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fully local, deterministic English-ish formant/phoneme tone synthesizer.
 * This is a small independent source of audible melodic vocal timbres, not an
 * intelligible TTS engine or a substitute for a recorded human singing voice.
 *
 * Output: 44.1kHz mono PCM. PcmMix then fits words to the song's beat grid.
 */
public final class BuiltinVocalSynth {
    public static final int RATE=44100;
    private static final double TAU=Math.PI*2;
    private BuiltinVocalSynth() {}

    private static final class Unit {
        final char symbol;
        final int samples;
        Unit(char symbol, double seconds) {
            this.symbol=symbol;
            this.samples=Math.max(64,(int)Math.round(RATE*seconds));
        }
    }

    private static boolean isVowel(char c) { return "aeiouy".indexOf(c)>=0; }
    public static float[] renderWord(String input) {
        if(input==null)throw new IllegalArgumentException("Missing word");
        String word=input.toLowerCase(Locale.ROOT).replaceAll("[^a-z]","");
        if(word.isEmpty())throw new IllegalArgumentException(
            "Built-in voice supports words with English A-Z letters only");
        if(word.length()>48)throw new IllegalArgumentException("Built-in voice word exceeds 48 letters");
        List<Unit> units=new ArrayList<>();
        for(int i=0;i<word.length();) {
            char c=word.charAt(i);
            String pair=i+1<word.length()?word.substring(i,i+2):"";
            if(pair.equals("sh")||pair.equals("ch")){units.add(new Unit('s',.093));i+=2;continue;}
            if(pair.equals("th")||pair.equals("ph")){units.add(new Unit('f',.088));i+=2;continue;}
            if(pair.equals("ng")){units.add(new Unit('n',.079));i+=2;continue;}
            if(pair.equals("ee")||pair.equals("ea")){units.add(new Unit('i',.155));i+=2;continue;}
            if(pair.equals("oo")){units.add(new Unit('u',.155));i+=2;continue;}
            if(pair.equals("ai")||pair.equals("ay")){units.add(new Unit('e',.145));i+=2;continue;}
            if(pair.equals("ou")||pair.equals("ow")){units.add(new Unit('o',.14));i+=2;continue;}
            if(c=='e' && i==word.length()-1 && units.stream().anyMatch(u->isVowel(u.symbol))){
                i++;continue;
            }
            boolean vowel=isVowel(c);
            double seconds=vowel?.145:"mnrlwy".indexOf(c)>=0?.080:
                "sfxzhvj".indexOf(c)>=0?.085:.050;
            units.add(new Unit(c,seconds));
            i++;
        }
        if(units.isEmpty())units.add(new Unit('a',.14));
        int frames=0;
        for(Unit unit:units)frames+=unit.samples;
        // Bounds a single word to avoid runaway memory even with pathological input.
        if(frames<=0||frames>RATE*5)throw new IllegalArgumentException("Built-in voice word too long");
        float[] samples=new float[frames];
        int seed=word.hashCode()^0x53415942;
        double phase=0;
        int at=0;
        for(Unit u:units){
            boolean vowel=isVowel(u.symbol);
            boolean nasal="mnrlw".indexOf(u.symbol)>=0;
            boolean fricative="sfxzhvj".indexOf(u.symbol)>=0;
            boolean stop="bdgkptcq".indexOf(u.symbol)>=0;
            int[] f=formants(u.symbol);
            double[] harmonic=new double[20];
            double weight=0;
            for(int h=1;h<harmonic.length;h++){
                double hz=176.0*h;
                double a=.055/h;
                a+=.9*Math.exp(-Math.pow((hz-f[0])/210.0,2));
                a+=.55*Math.exp(-Math.pow((hz-f[1])/300.0,2));
                a+=.34*Math.exp(-Math.pow((hz-f[2])/370.0,2));
                harmonic[h]=a/Math.sqrt(h);
                weight+=harmonic[h];
            }
            weight=Math.max(.0001,weight);
            for(int j=0;j<u.samples;j++){
                double sec=(at+j)/(double)RATE;
                double pos=j/(double)u.samples;
                phase+=TAU*(176+2.3*Math.sin(TAU*5.2*sec))/RATE;
                if(phase>TAU)phase-=TAU;
                double voice=0;
                if(vowel||nasal||!fricative&&!stop){
                    for(int h=1;h<harmonic.length;h++)voice+=harmonic[h]*Math.sin(phase*h);
                    voice/=weight;
                }
                seed=seed*1664525+1013904223;
                double noise=((seed>>>8)/(double)0xFFFFFF)*2-1;
                if(fricative)voice=voice*.15+noise*.56;
                else if(stop)voice=noise*(pos<.19?.40:.04)+voice*.12;
                else if(nasal)voice*=.72;
                // Each grapheme is independently audible but transitions are softened.
                double fade=Math.min(1,Math.min(j/(RATE*.008),
                    (u.samples-1-j)/(RATE*.014)));
                fade=Math.max(0,fade);
                double amp=vowel?.58:nasal?.44:fricative?.34:.24;
                samples[at+j]=(float)Math.max(-.82,Math.min(.82,amp*fade*voice));
            }
            at+=u.samples;
        }
        // Avoid word-edge clicks before tempo fitting, including one-unit words.
        int edge=Math.min(350,Math.max(1,frames/8));
        for(int i=0;i<edge;i++){
            double fade=(i+.5)/edge;
            samples[i]*=(float)fade;
            samples[frames-1-i]*=(float)fade;
        }
        return samples;
    }
    private static int[] formants(char c) {
        switch(c){
            case 'a':return new int[]{790,1230,2650};
            case 'e':return new int[]{460,1890,2650};
            case 'i': case 'y':return new int[]{300,2300,3000};
            case 'o':return new int[]{525,980,2530};
            case 'u':return new int[]{340,1000,2230};
            case 'm':case 'n':return new int[]{300,1100,2300};
            case 'l':case 'r':case 'w':return new int[]{445,1470,2640};
            default:return new int[]{450,1550,2500};
        }
    }
}
