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
    List<WordTimeline.Cue> cues=WordTimeline.build("hello world",g,12);
    if(cues.isEmpty() || !cues.get(0).text.equals("hello"))throw new AssertionError("cues");
    for(WordTimeline.Cue cue:cues){
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
    float[] pitched=PitchDsp.render(new float[rate/4],rate/3,4);
    if(pitched.length!=rate/3)throw new AssertionError("DSP cue duration");\n    System.out.println("PASS beat~"+g.bpm+" BPM; "+cues.size()+" cues; timed text; stereo mix; limiter");
  }
}