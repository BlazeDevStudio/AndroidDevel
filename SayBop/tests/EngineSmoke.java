import com.saybop.app.*;
import java.util.*;
public final class EngineSmoke {
  public static void main(String[] args) {
    int rate=44100;
    float[] background=new float[rate*12*2];
    for (int beat=0;beat<24;beat++) {
      int pos=(int)(beat*.5*rate);
      for(int j=0;j<500;j++)if(pos+j<background.length/2){
        background[(pos+j)*2]=.6f;background[(pos+j)*2+1]=.6f;
      }
    }
    BeatAnalyzer.Grid g=BeatAnalyzer.detect(background,rate);
    if (Math.abs(g.bpm-120)>8 && Math.abs(g.bpm-60)>8)throw new AssertionError("BPM "+g.bpm);
    BeatAnalyzer.Grid corrected=BeatAnalyzer.correct(g,100,-125);
    if(Math.abs(corrected.bpm-100)>1e-8 || corrected.phaseSeconds<0
       || corrected.phaseSeconds>=corrected.beatSeconds())throw new AssertionError("Manual grid correction");
    try { BeatAnalyzer.correct(g,400,0);throw new AssertionError("Invalid BPM accepted"); }
    catch(IllegalArgumentException expected) {}
    List<WordTimeline.Cue> cues=WordTimeline.build("hello world",g,12);
    if(cues.isEmpty() || !cues.get(0).text.equals("hello"))throw new AssertionError("cues");
    for(WordTimeline.Cue cue:cues){
      double snapped=(cue.startSeconds-g.phaseSeconds)/g.beatSeconds();
      if(Math.abs(snapped*2-Math.round(snapped*2))>1e-6)throw new AssertionError("Off-grid word onset");
      if(cue.startSeconds<0 || cue.endSeconds>12 || cue.endSeconds<=cue.startSeconds)
        throw new AssertionError("cue range");
      if(WordTimeline.at(cues, (cue.startSeconds+cue.endSeconds)/2)!=cue)
        throw new AssertionError("animation timestamp not aligned");
    }
    List<float[]> voices=Arrays.asList(new float[rate/4],new float[rate/4]);
    for(float[] v:voices)Arrays.fill(v,.25f);
    float[] mixed=PcmMix.compose(background,cues,voices);
    if(mixed.length!=background.length)throw new AssertionError("mix duration");
    for(float x:mixed)if(!Float.isFinite(x) || x>1 || x< -1)throw new AssertionError("PCM range");
    if (MelodicVocal.estimateSyllables("hello")!=2)throw new AssertionError("Syllable nuclei");
    if (MelodicVocal.estimateSyllables("make")!=1)throw new AssertionError("Silent E");
    if (MelodicVocal.estimateSyllables("rhythm")<1)throw new AssertionError("Y vowel");
    if (MelodicVocal.estimateSyllables("rhythm")>4)throw new AssertionError("Syllable upper bound");
    float[] testWord=new float[rate/3];
    for (int i=0;i<testWord.length;i++)testWord[i]=(float)(.22*Math.sin(2*Math.PI*180*i/rate));
    float[] melodic=MelodicVocal.render(testWord,rate/2,"hello",1,0);
    if (melodic.length!=rate/2)throw new AssertionError("Vocal duration");
    double energy=0;
    for (float x:melodic){if(!Float.isFinite(x)||Math.abs(x)>2)throw new AssertionError("Vocal PCM range");energy+=x*x;}
    if (energy<1)throw new AssertionError("Melodic render unexpectedly silent");
    float[] pitched=PitchDsp.render(new float[rate/4],rate/3,4);
    if(pitched.length!=rate/3)throw new AssertionError("DSP cue duration");
    System.out.println("PASS beat~"+g.bpm+" BPM; "+cues.size()+" cues; timed text; stereo mix; limiter");
  }
}