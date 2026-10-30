package com.saybop.app;

import android.content.Context;
import android.content.Intent;
import android.app.Activity;
import android.app.Instrumentation;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.TextView;
import java.util.concurrent.atomic.AtomicReference;
import android.graphics.Bitmap;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

/** Real Android emulator built-in voice + music + reactive MP4 test, no system TTS required. */
@RunWith(AndroidJUnit4.class)
public final class ExportSmokeTest {
    @Test public void syntheticMusicalWordsProducePlayableAvcAacMp4() throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        final int rate=PcmMix.RATE;
        final int seconds=3;
        float[] backing=new float[rate*seconds*2];
        for(int i=0;i<backing.length/2;i++){
            float v=(float)(.13*Math.sin(2*Math.PI*220*i/rate));
            backing[i*2]=v;backing[i*2+1]=v;
        }
        float[] a=BuiltinVocalSynth.renderWord("HELLO");
        float[] b=BuiltinVocalSynth.renderWord("WORLD");
        assertTrue("Built-in vocal synthesis produced no samples",a.length>rate/5 && b.length>rate/5);
        double vowelEnergy=0;
        for(float v:a)vowelEnergy+=v*v;
        assertTrue("Built-in vocal synthesis is silent",vowelEnergy>5);
        BeatAnalyzer.Grid grid=new BeatAnalyzer.Grid(120,0);
        List<WordTimeline.Cue> cues=Arrays.asList(
            new WordTimeline.Cue("HELLO",0,0,.5,.9),
            new WordTimeline.Cue("WORLD",1,0,1.0,1.4));
        AudioVisuals reactive=AudioVisuals.analyze(backing,rate);
        assertTrue("Synthetic music should have envelope",reactive.loudness(.5)>0.1f);
        VideoRenderer still=new VideoRenderer(cues,grid,3,reactive);
        Bitmap artwork=still.frame(.62);
        assertEquals(VideoRenderer.WIDTH,artwork.getWidth());
        assertEquals(VideoRenderer.HEIGHT,artwork.getHeight());
        // One reusable Bitmap must be stable at a given timestamp but differ across song sections.
        VideoRenderer director=new VideoRenderer(cues,grid,5,reactive);
        Bitmap first=director.frame(.75);
        int[] firstPixels=new int[64];
        for(int i=0;i<64;i++)firstPixels[i]=first.getPixel(20+(i%8)*94,140+(i/8)*130);
        Bitmap second=director.frame(2.75);
        int changed=0;
        for(int i=0;i<64;i++)if(firstPixels[i]!=second.getPixel(20+(i%8)*94,140+(i/8)*130))changed++;
        assertTrue("Auto scene did not visibly change",changed>14);
        assertEquals(0,SceneDirector.scene(grid,.75));
        assertEquals(1,SceneDirector.scene(grid,2.75));
        assertEquals("Preview/export scene must be seek-deterministic",1,SceneDirector.scene(grid,2.75));
        float[] mixed=PcmMix.compose(backing,cues,Arrays.asList(a,b));
        Uri exported=null;
        try {
            exported=Mp4Exporter.export(ctx,mixed,cues,grid,"SayBop-QA-Codec-Test.mp4",5,reactive);
            assertNotNull("MediaStore returned no URI",exported);
            MediaMetadataRetriever meta=new MediaMetadataRetriever();
            try {
                meta.setDataSource(ctx,exported);
                assertEquals("720",meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                assertEquals("1280",meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                String duration=meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                assertNotNull("MP4 duration metadata missing",duration);
                long d=Long.parseLong(duration);
                assertTrue("MP4 duration "+d,d>=2800 && d<=3400);
                Bitmap frame=meta.getFrameAtTime(600000,MediaMetadataRetriever.OPTION_CLOSEST);
                assertNotNull("MP4 H.264 frame not decodable",frame);
                frame.recycle();
            } finally {meta.release();}
            MediaExtractor extractor=new MediaExtractor();
            int video=-1,audio=-1;
            try {
                extractor.setDataSource(ctx,exported,null);
                for(int i=0;i<extractor.getTrackCount();i++){
                    MediaFormat f=extractor.getTrackFormat(i);
                    String mime=f.getString(MediaFormat.KEY_MIME);
                    if("video/avc".equals(mime))video=i;
                    if("audio/mp4a-latm".equals(mime))audio=i;
                }
            } finally {extractor.release();}
            assertTrue("Missing H264 track",video>=0);
            assertTrue("Missing AAC track",audio>=0);
            for(int track:new int[]{video,audio}) {
                MediaExtractor reader=new MediaExtractor();
                try {
                    reader.setDataSource(ctx,exported,null);
                    reader.selectTrack(track);
                    ByteBuffer buffer=ByteBuffer.allocate(1024*1024);
                    long prev=-1;int samples=0;
                    while(true){
                        int n=reader.readSampleData(buffer,0);
                        if(n<0)break;
                        long t=reader.getSampleTime();
                        assertTrue("PTS regression",t>=prev);
                        prev=t;
                        samples++;reader.advance();
                        if(samples>600)fail("Unbounded MP4 samples");
                    }
                    assertTrue("Encoded track is empty",samples>0);
                } finally {reader.release();}
            }
        } finally {
            if(exported!=null)ctx.getContentResolver().delete(exported,null,null);
        }
    }
    /** First-run path exercised through real Android UI, no external TTS or imported file. */
    @Test public void builtinDemoButtonGeneratesPlayablePreviewWithoutSystemTts() throws Exception {
        Instrumentation inst=InstrumentationRegistry.getInstrumentation();
        Context context=inst.getTargetContext();
        Intent launch=new Intent(context,MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final Activity activity=inst.startActivitySync(launch);
        assertNotNull("MainActivity did not launch",activity);
        final AtomicReference<String> last=new AtomicReference<>("");
        try {
            inst.runOnMainSync(()->{
                Button demo=activity.findViewById(R.id.demo_beat_button);
                Button generate=activity.findViewById(R.id.generate_button);
                assertNotNull("Demo beat control missing",demo);
                assertNotNull("Generate button missing",generate);
                assertTrue("Built-in demo beat not selectable",demo.performClick());
                assertTrue("Generate button did not accept click",generate.performClick());
            });
            final long deadline=SystemClock.uptimeMillis()+95000L;
            boolean ready=false;
            while(SystemClock.uptimeMillis()<deadline){
                inst.runOnMainSync(()->{
                    TextView status=activity.findViewById(R.id.generation_status);
                    assertNotNull(status);
                    last.set(status.getText().toString());
                });
                if(last.get().startsWith("Ready:")){ready=true;break;}
                if(last.get().startsWith("Generate failed:"))break;
                Thread.sleep(300);
            }
            assertTrue("Built-in UI generation incomplete: "+last.get(),ready);
            inst.runOnMainSync(()->{
                Button play=activity.findViewById(R.id.preview_play_button);
                Button export=activity.findViewById(R.id.video_export_button);
                assertNotNull("Preview control missing",play);
                assertNotNull("Export control missing",export);
                assertTrue("Preview must be enabled after generation",play.isEnabled());
                assertTrue("MP4 export must be enabled after generation",export.isEnabled());
            });
        } finally {
            inst.runOnMainSync(activity::finish);
        }
    }

}
