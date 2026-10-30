package com.saybop.app;

import android.app.*;
import android.os.*;
import android.content.*;
import android.media.MediaPlayer;
import android.net.Uri;
import android.graphics.*;
import android.speech.tts.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Native, entirely offline text -> beat -> generated voice -> animated MP4 prototype. */
public class MainActivity extends Activity {
    private TextToSpeech tts;
    private boolean ttsReady=false,busy=false;
    private final ArrayList<Voice> voiceOptions=new ArrayList<>();
    private final ConcurrentHashMap<String,CountDownLatch> pending=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String,Boolean> finished=new ConcurrentHashMap<>();
    private Uri selectedMusic;
    private boolean demoMusic=false;
    private EditText typed;
    private TextView statusLabel,songLabel;
    private Button create,export,play;
    private Spinner voices, animationModes;
    private EditText bpmInput, beatShiftInput;
    private int currentAnimationMode=0;
    private KineticPreview preview;
    private MediaPlayer player;
    private float[] mixed;
    private List<WordTimeline.Cue> cues;
    private BeatAnalyzer.Grid grid;
    private AudioVisuals songVisuals;
    private File previewFile;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){if(player!=null&&player.isPlaying()){
        preview.invalidate();ui.postDelayed(this,33);}}};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(9,15,29));
        getWindow().setNavigationBarColor(Color.rgb(9,15,29));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(dp(18),dp(20),dp(18),dp(24));
        layout.setBackgroundColor(Color.rgb(9,15,29));
        scroll.addView(layout);setContentView(scroll);
        LinearLayout brandRow=new LinearLayout(this);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView brandMark=new ImageView(this);
        brandMark.setImageResource(R.drawable.saybop_brand_mark);
        brandRow.addView(brandMark,new LinearLayout.LayoutParams(dp(56),dp(56)));
        TextView heading=label("SAYBOP",29,Color.rgb(244,247,255));
        heading.setTypeface(null,Typeface.BOLD);
        LinearLayout.LayoutParams headingParams=new LinearLayout.LayoutParams(-1,-2);
        headingParams.leftMargin=dp(12);
        brandRow.addView(heading,headingParams);
        layout.addView(brandRow);
        layout.addView(label("Type it. Bop it. Share it.",13,Color.rgb(177,195,219)));
        space(layout,18);
        layout.addView(label("1   TYPE YOUR PHRASE",13,Color.WHITE));
        typed=new EditText(this);typed.setSingleLine(false);typed.setMinLines(2);typed.setMaxLines(5);
        typed.setText("Make every word hit the beat");typed.setTextColor(Color.WHITE);
        typed.setHintTextColor(Color.GRAY);typed.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(74,164,245)));
        typed.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        layout.addView(typed);space(layout,10);
        layout.addView(label("2   CHOOSE A LOCAL SONG",13,Color.WHITE));
        Button choose=button("CHOOSE MP3 / WAV / M4A",layout);
        songLabel=label("No song selected",13,Color.rgb(177,195,219));layout.addView(songLabel);
        choose.setOnClickListener(v->{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("audio/*");startActivityForResult(i,55);
        });space(layout,12);
        Button builtInBeat=button("USE ORIGINAL DEMO BEAT (120 BPM)",layout);
        builtInBeat.setId(R.id.demo_beat_button);
        builtInBeat.setOnClickListener(v->{
            demoMusic=true;selectedMusic=null;
            songLabel.setText("Built-in 12s demo beat — 120 BPM (offline)");
        });
        space(layout,12);
        layout.addView(label("3   VOICE",13,Color.WHITE));
        voices=new Spinner(this);
        ArrayAdapter<String> initial=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Built-in Synth (robotic, works offline)"});
        voices.setAdapter(initial);layout.addView(voices);space(layout,14);
        layout.addView(label("ANIMATION",13,Color.WHITE));
        animationModes=new Spinner(this);
        animationModes.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,
            new String[]{"Beat Pop","Slide + Fade","Pulse + Bounce","Spectrum Orbit","Waveform Drift","Auto Director"}));
        layout.addView(animationModes);space(layout,8);
        layout.addView(label("BPM  (blank = auto)",13,Color.WHITE));
        bpmInput=new EditText(this);bpmInput.setSingleLine(true);
        bpmInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|
            android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        bpmInput.setHint("Auto");bpmInput.setTextColor(Color.WHITE);
        layout.addView(bpmInput);
        layout.addView(label("BEAT OFFSET  (milliseconds)",13,Color.WHITE));
        beatShiftInput=new EditText(this);beatShiftInput.setSingleLine(true);
        beatShiftInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|
            android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        beatShiftInput.setText("0");beatShiftInput.setTextColor(Color.WHITE);
        layout.addView(beatShiftInput);space(layout,14);
        create=button("GENERATE SAYBOP",layout);
        create.setId(R.id.generate_button);
        create.setOnClickListener(v->generate());
        statusLabel=label("Built-in voice ready. Choose a song or demo beat.",13,Color.rgb(163,189,217));
        statusLabel.setId(R.id.generation_status);
        layout.addView(statusLabel);space(layout,14);
        preview=new KineticPreview(this,()->player!=null?player.getCurrentPosition()/1000.0:0.0);
        preview.setBackgroundColor(Color.rgb(5,10,24));
        layout.addView(preview,new LinearLayout.LayoutParams(-1,dp(330)));
        space(layout,12);
        play=button("PLAY AUDIO + ANIMATION",layout);
        play.setId(R.id.preview_play_button);
        play.setEnabled(false);play.setOnClickListener(v->playPreview());
        export=button("EXPORT 9:16 MP4",layout);
        export.setId(R.id.video_export_button);export.setEnabled(false);
        export.setOnClickListener(v->exportVideo());
        space(layout,10);
        layout.addView(label("Audio and video are generated on your device. No accounts, uploads, or cloud services.",12,
                Color.rgb(135,153,176)));
        tts=new TextToSpeech(this, result->{
            if(result==TextToSpeech.SUCCESS){ttsReady=true;loadVoices();}
            else notifyStatus("Built-in Synth ready. Android TTS unavailable; choose demo beat or local music.");
        });
        if(tts!=null)tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
            @Override public void onStart(String id){}
            @Override public void onDone(String id){complete(id,true);}
            @Override public void onError(String id){complete(id,false);}
            @Override public void onError(String id,int code){complete(id,false);}
        });
    }
    private void complete(String id,boolean ok){finished.put(id,ok);CountDownLatch l=pending.get(id);if(l!=null)l.countDown();}
    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    private static void space(LinearLayout l,int dip){
        android.view.View v=new android.view.View(l.getContext());
        l.addView(v,new LinearLayout.LayoutParams(1,(int)(dip*l.getResources().getDisplayMetrics().density)));
    }
    private TextView label(String t,int sp,int color){
        TextView v=new TextView(this);v.setText(t);v.setTextSize(sp);v.setTextColor(color);v.setPadding(0,dp(4),0,dp(4));return v;
    }
    private Button button(String title,LinearLayout root){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(22,113,230)));
        root.addView(b,new LinearLayout.LayoutParams(-1,dp(49)));return b;
    }
    private void notifyStatus(String msg){runOnUiThread(()->statusLabel.setText(msg));}
    private void loadVoices(){
        runOnUiThread(()->{
            voiceOptions.clear();ArrayList<String> names=new ArrayList<>();
            names.add("Built-in Synth (robotic, works offline)");
            Set<Voice> all=ttsReady && tts!=null?tts.getVoices():null;
            if(all!=null){
                ArrayList<Voice> sorted=new ArrayList<>(all);
                sorted.sort(Comparator.comparing(v->v.getLocale().toLanguageTag()+v.getName()));
                for(Voice v:sorted){
                    if(v.isNetworkConnectionRequired())continue;
                    voiceOptions.add(v);names.add(v.getLocale().toLanguageTag()+" / "+v.getName());
                }
            }
            voices.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));
            voices.setSelection(0);
            notifyStatus("Built-in Synth available; "+voiceOptions.size()+" optional offline Android TTS voices.");
        });
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==55 && result==RESULT_OK && data!=null){
            selectedMusic=data.getData();
            if(selectedMusic!=null){
                demoMusic=false;
                try{getContentResolver().takePersistableUriPermission(selectedMusic,Intent.FLAG_GRANT_READ_URI_PERMISSION);}
                catch(SecurityException ignored){}
                songLabel.setText("Selected: "+selectedMusic.getLastPathSegment());
            }
        }
    }
    private void generate(){
        if(busy)return;
        if(selectedMusic==null && !demoMusic){notifyStatus("Choose a local song or use the built-in demo beat.");return;}
        String phrase=typed.getText().toString().trim();
        if(phrase.isEmpty()){notifyStatus("Type something to sing.");return;}
        final int voiceChoice=voices.getSelectedItemPosition();
        final boolean useBuiltIn=voiceChoice<=0;
        if(!useBuiltIn && (!ttsReady || voiceChoice-1>=voiceOptions.size())){
            notifyStatus("Selected Android TTS voice is unavailable; try Built-in Synth.");return;
        }
        final Voice selected=useBuiltIn?null:voiceOptions.get(voiceChoice-1);
        final boolean chosenDemo=demoMusic;
        final Uri chosenMusic=selectedMusic;
        final int chosenAnimation=Math.max(0,animationModes.getSelectedItemPosition());
        final String bpmOverride=bpmInput.getText().toString().trim();
        final String offsetOverride=beatShiftInput.getText().toString().trim();
        setBusy(true);mixed=null;play.setEnabled(false);export.setEnabled(false);
        new Thread(()->{
            try {
                notifyStatus("Decoding selected music locally…");
                AudioIO.Track music=chosenDemo?new AudioIO.Track(DemoBeat.create()):
                    AudioIO.decode(this,chosenMusic,18);
                double bpm=bpmOverride.isEmpty()?0:Double.parseDouble(bpmOverride);
                double offsetMs=offsetOverride.isEmpty()?0:Double.parseDouble(offsetOverride);
                SongPipeline.Plan plan=SongPipeline.plan(phrase,music.stereo,bpm,offsetMs);
                List<float[]> voice=new ArrayList<>();
                for(int i=0;i<plan.words.size();i++) {
                    notifyStatus("Synthesizing "+(i+1)+"/"+plan.words.size()+" words…");
                    if(useBuiltIn){
                        voice.add(BuiltinVocalSynth.renderWord(plan.words.get(i)));
                        continue;
                    }
                    File wav=new File(getCacheDir(),"voice_"+i+"_"+System.nanoTime()+".wav");
                    String id="saybop_"+i+"_"+System.nanoTime();
                    CountDownLatch latch=new CountDownLatch(1);
                    pending.put(id,latch);
                    try {
                        tts.setVoice(selected);
                        // A small melody pattern; installed engine remains the source of speech.
                        tts.setPitch(1.0f);tts.setSpeechRate(1.06f); // Melodic pitch is processed in offline PCM, not delegated to TTS.
                        int rc=tts.synthesizeToFile(plan.words.get(i),new Bundle(),wav,id);
                        if(rc!=TextToSpeech.SUCCESS)throw new IOException("TTS engine rejected word: "+plan.words.get(i));
                        if(!latch.await(25,TimeUnit.SECONDS)||!Boolean.TRUE.equals(finished.remove(id)))
                            throw new IOException("Offline TTS could not synthesize: "+plan.words.get(i));
                        voice.add(AudioIO.wavMono(wav));
                    } finally {pending.remove(id);finished.remove(id);wav.delete();}
                }
                notifyStatus("Synchronizing voice to beats and rendering preview…");
                float[] output=SongPipeline.mix(plan,voice);
                File playable=AudioIO.writePreviewWav(this,output);
                runOnUiThread(()->{
                    stopPreview();mixed=output;cues=plan.cues;grid=plan.grid;songVisuals=plan.visuals;previewFile=playable;
                    currentAnimationMode=chosenAnimation;
                    preview.setTimeline(cues,grid,currentAnimationMode,songVisuals);play.setEnabled(true);export.setEnabled(true);
                    notifyStatus(String.format(Locale.US,"Ready: %.1f seconds | %.0f BPM | %d word cues",
                        output.length/(2.0*PcmMix.RATE),plan.grid.bpm,plan.cues.size()));
                });
            } catch(Exception e){notifyStatus("Generate failed: "+e.getMessage());}
            finally {runOnUiThread(()->setBusy(false));}
        },"SayBopOfflineAudio").start();
    }
    private void setBusy(boolean b){busy=b;create.setEnabled(!b);if(b){play.setEnabled(false);export.setEnabled(false);}}
    private void stopPreview(){ui.removeCallbacks(refresh);if(player!=null){
        try{player.stop();}catch(Exception ignored){}player.release();player=null;}}
    private void playPreview(){
        if(previewFile==null)return;
        stopPreview();
        try{
            player=new MediaPlayer();player.setDataSource(previewFile.getAbsolutePath());player.prepare();
            player.setOnCompletionListener(mp->{ui.removeCallbacks(refresh);preview.invalidate();});
            player.start();ui.post(refresh);
        }catch(Exception e){notifyStatus("Audio playback error: "+e.getMessage());stopPreview();}
    }
    private void exportVideo(){
        if(busy||mixed==null)return;
        setBusy(true);
        final float[] audio=mixed;final List<WordTimeline.Cue> words=cues;
        final BeatAnalyzer.Grid beats=grid;
        final int mode=currentAnimationMode;
        final AudioVisuals reactive=songVisuals;
        new Thread(()->{
            try{
                notifyStatus("Encoding offline H.264 video and AAC audio…");
                String name="SayBop_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+".mp4";
                Uri out=Mp4Exporter.export(this,audio,words,beats,name,mode,reactive);
                notifyStatus("Saved MP4 to Movies/SayBop: "+name+"\n"+out);
            }catch(Exception e){notifyStatus("Export failed: "+e.getMessage());}
            finally{runOnUiThread(()->{setBusy(false);play.setEnabled(mixed!=null);export.setEnabled(mixed!=null);});}
        },"SayBopOfflineExport").start();
    }
    @Override protected void onDestroy(){
        stopPreview();ttsReady=false;if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();
    }
}
