import com.saybop.app.*;
import java.util.*;
public final class EngineSmoke {
  public static void main(String[] args) {
    int rate=44100;
    float[] syllables=BuiltinVocalSynth.renderWord("hello");
    float[] other=BuiltinVocalSynth.renderWord("world");
    float[] again=BuiltinVocalSynth.renderWord("hello");
    if(syllables.length<rate/5 || syllables.length>rate*2 ||
       other.length<rate/5)throw new AssertionError("Built-in word duration");
    if(syllables.length!=again.length)throw new AssertionError("Nondeterministic length");
    double voicePower=0;
    for(int i=0;i<syllables.length;i++){
      float v=syllables[i];
      if(!Float.isFinite(v)||Math.abs(v)>.83f || v!=again[i])
        throw new AssertionError("Built-in voice invalid/different PCM");
      voicePower+=v*v;
    }
    if(voicePower<5)throw new AssertionError("Built-in voice unexpectedly silent");
    try{BuiltinVocalSynth.renderWord("1234");throw new AssertionError("Invalid word accepted");}
    catch(IllegalArgumentException expected){}
    float[] demo=DemoBeat.create();
    if(demo.length!=rate*12*2)throw new AssertionError("Demo music duration");
    AudioVisuals demoEnvelope=AudioVisuals.analyze(demo,rate);
    if(demoEnvelope.loudness(.02)<.01 || demoEnvelope.bass(.2)<.01)
      throw new AssertionError("Demo music is silent");
    BeatAnalyzer.Grid demoGrid=BeatAnalyzer.detect(demo,rate);
    if(!Double.isFinite(demoGrid.bpm)||demoGrid.bpm<60||demoGrid.bpm>180)
      throw new AssertionError("Demo beat bpm invalid "+demoGrid.bpm);
    List<WordTimeline.Cue> demoCues=WordTimeline.build("hello world",new BeatAnalyzer.Grid(120,0),12);
    float[] demoMix=PcmMix.compose(demo,demoCues,Arrays.asList(syllables,other));
    if(demoMix.length!=demo.length)throw new AssertionError("Full built-in pipeline mismatched duration");
    for(float v:demoMix)if(!Float.isFinite(v)||Math.abs(v)>1)
      throw new AssertionError("Full built-in pipeline clipping");
    
    float[] background=new float[rate*12*2];
    for (int beat=0;beat<24;beat++) {
      int pos=(int)(beat*.5*rate);
      for(int j=0;j<500;j++)if(pos+j<background.length/2){
        background[(pos+j)*2]=.6f;background[(pos+j)*2+1]=.6f;
      }
    }
    AudioVisuals visuals=AudioVisuals.analyze(background,rate);
    float early=visuals.loudness(.005),between=visuals.loudness(.26);
    if(!(early>between+.1f))throw new AssertionError("Music loudness is not reactive to beat hits");
    if(!Float.isFinite(visuals.attack(.01))||visuals.attack(.01)<0||visuals.attack(.01)>1)throw new AssertionError("Attack bounds");
    float[] blank=new float[rate*2];
    AudioVisuals silence=AudioVisuals.analyze(blank,rate);
    if(silence.loudness(.1)!=0||silence.attack(.1)!=0)throw new AssertionError("Silence not zero");
    float[] shifted=background.clone();
    for(int i=0;i<shifted.length;i++)shifted[i]*=.25f;
    AudioVisuals scaled=AudioVisuals.analyze(shifted,rate);
    if(!Float.isFinite(scaled.bass(.15)))throw new AssertionError("Bass finite");
    try{AudioVisuals.analyze(new float[]{1},rate);throw new AssertionError("Odd PCM accepted");}
    catch(IllegalArgumentException expected){}
    BeatAnalyzer.Grid g=BeatAnalyzer.detect(background,rate);
    BeatAnalyzer.Grid sectionGrid=new BeatAnalyzer.Grid(120,.10);
    if(SceneDirector.scene(sectionGrid,.4)!=0 || SceneDirector.scene(sectionGrid,2.11)!=1)
      throw new AssertionError("Section scene boundary");
    if(SceneDirector.scene(sectionGrid,2.11)!=SceneDirector.scene(sectionGrid,2.11))
      throw new AssertionError("Nondeterministic scene seek");
    if(SceneDirector.scene(sectionGrid,12.11)!=0)
      throw new AssertionError("Six-scene cyclic sequence");
    if(SceneDirector.typographyMotion(5,4)!=1||SceneDirector.typographyMotion(2,4)!=2)
      throw new AssertionError("Motion mode mismatch");
    float flash=SceneDirector.downbeatFlash(sectionGrid,.10);
    if(flash<.99 || SceneDirector.downbeatFlash(sectionGrid,1.10)>.001)
      throw new AssertionError("Flash not beat aligned");
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