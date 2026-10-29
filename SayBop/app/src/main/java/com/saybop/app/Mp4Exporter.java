package com.saybop.app;

import android.content.*;
import android.graphics.Bitmap;
import android.media.*;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.*;
import java.nio.*;
import java.util.*;

/** Offline H.264/AAC MP4 writer. Requires an Android video encoder with flexible YUV420 input images. */
public final class Mp4Exporter {
    private static final int FPS=30;
    private Mp4Exporter(){}
    private static class Enc {
        MediaCodec codec;int track=-1;boolean ended=false;
        Enc(MediaCodec c){codec=c;}
    }
    private static class Sample {
        Enc encoder;byte[] data;MediaCodec.BufferInfo info;
        Sample(Enc e,byte[] d,MediaCodec.BufferInfo src) {
            encoder=e;data=d;info=new MediaCodec.BufferInfo();
            info.set(0,d.length,src.presentationTimeUs,src.flags);
        }
    }
    private static class State {
        MediaMuxer muxer;boolean started;
        ArrayList<Sample> pending=new ArrayList<>();
        Enc video,audio;
        void write(Sample s){muxer.writeSampleData(s.encoder.track,ByteBuffer.wrap(s.data),s.info);}
        void maybeStart(){
            if(!started && video.track>=0 && audio.track>=0){
                muxer.start();started=true;
                for(Sample s:pending)write(s);
                pending.clear();
            }
        }
    }
    private static void pump(State s,Enc e) {
        MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
        for(int attempt=0;attempt<20;attempt++){
            int idx=e.codec.dequeueOutputBuffer(info,0);
            if(idx==MediaCodec.INFO_TRY_AGAIN_LATER)return;
            if(idx==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                e.track=s.muxer.addTrack(e.codec.getOutputFormat());s.maybeStart();continue;
            }
            if(idx>=0){
                if(info.size>0 && (info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0){
                    ByteBuffer b=e.codec.getOutputBuffer(idx);
                    if(b==null)throw new IllegalStateException("Encoder output null");
                    b.position(info.offset);b.limit(info.offset+info.size);
                    byte[] bytes=new byte[info.size];b.get(bytes);
                    Sample sample=new Sample(e,bytes,info);
                    if(s.started)s.write(sample);else s.pending.add(sample);
                }
                if((info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0)e.ended=true;
                e.codec.releaseOutputBuffer(idx,false);
            }
        }
    }
    private static void queueVideo(State state,VideoRenderer renderer,int frameNo,boolean last){
        Enc e=state.video;
        int idx=e.codec.dequeueInputBuffer(100000);
        if(idx<0){pump(state,e);pump(state,state.audio);idx=e.codec.dequeueInputBuffer(100000);}
        if(idx<0)throw new IllegalStateException("Video encoder input unavailable");
        long pts=frameNo*1_000_000L/FPS;
        if(last){e.codec.queueInputBuffer(idx,0,0,pts,MediaCodec.BUFFER_FLAG_END_OF_STREAM);return;}
        android.media.Image img=e.codec.getInputImage(idx);
        if(img==null)throw new IllegalStateException("Device lacks flexible YUV420 encoder image input");
        try{putYuv(renderer.frame(frameNo/(double)FPS),img);}finally{img.close();}
        e.codec.queueInputBuffer(idx,0,VideoRenderer.WIDTH*VideoRenderer.HEIGHT*3/2,pts,0);
    }
    private static int cap(int x){return x<0?0:Math.min(255,x);}
    private static void putYuv(Bitmap b,android.media.Image im){
        int w=b.getWidth(),h=b.getHeight();
        int[] px=new int[w*h];b.getPixels(px,0,w,0,0,w,h);
        android.media.Image.Plane[] planes=im.getPlanes();
        ByteBuffer y=planes[0].getBuffer(),u=planes[1].getBuffer(),v=planes[2].getBuffer();
        int ys=planes[0].getRowStride(),yp=planes[0].getPixelStride();
        int us=planes[1].getRowStride(),up=planes[1].getPixelStride();
        int vs=planes[2].getRowStride(),vp=planes[2].getPixelStride();
        for(int yy=0;yy<h;yy++)for(int xx=0;xx<w;xx++){
            int color=px[yy*w+xx],r=(color>>16)&255,g=(color>>8)&255,bl=color&255;
            int l=cap((int)(16+0.257*r+0.504*g+0.098*bl));
            y.put(yy*ys+xx*yp,(byte)l);
            if((xx&1)==0 && (yy&1)==0){
                int uu=cap((int)(128-0.148*r-0.291*g+0.439*bl));
                int vv=cap((int)(128+0.439*r-0.368*g-0.071*bl));
                u.put((yy/2)*us+(xx/2)*up,(byte)uu);
                v.put((yy/2)*vs+(xx/2)*vp,(byte)vv);
            }
        }
    }
    private static int queueAudio(State s,float[] stereo,int startFrame,int untilFrame) {
        Enc e=s.audio;
        int idx=e.codec.dequeueInputBuffer(100000);
        if(idx<0){pump(s,e);pump(s,s.video);idx=e.codec.dequeueInputBuffer(100000);}
        if(idx<0)throw new IllegalStateException("Audio encoder input unavailable");
        ByteBuffer b=e.codec.getInputBuffer(idx);
        if(b==null)throw new IllegalStateException("AAC input null");
        b.clear();b.order(ByteOrder.LITTLE_ENDIAN);
        int count=Math.min(untilFrame-startFrame,b.capacity()/4);
        for(int i=0;i<count;i++){
            b.putShort(PcmMix.pcm16(stereo[(startFrame+i)*2]));
            b.putShort(PcmMix.pcm16(stereo[(startFrame+i)*2+1]));
        }
        e.codec.queueInputBuffer(idx,0,count*4,startFrame*1_000_000L/PcmMix.RATE,0);
        return count;
    }
    public static Uri export(Context context,float[] audio,List<WordTimeline.Cue> cues,
                             BeatAnalyzer.Grid beat,String filename) throws Exception {
        File temp=new File(context.getCacheDir(),"saybop_export_"+System.nanoTime()+".mp4");
        State s=new State();boolean completed=false;
        try {
            MediaFormat vf=MediaFormat.createVideoFormat("video/avc",VideoRenderer.WIDTH,VideoRenderer.HEIGHT);
            vf.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            vf.setInteger(MediaFormat.KEY_BIT_RATE,1_600_000);
            vf.setInteger(MediaFormat.KEY_FRAME_RATE,FPS);
            vf.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,2);
            s.video=new Enc(MediaCodec.createEncoderByType("video/avc"));
            s.video.codec.configure(vf,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
            MediaFormat af=MediaFormat.createAudioFormat("audio/mp4a-latm",PcmMix.RATE,2);
            af.setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            af.setInteger(MediaFormat.KEY_BIT_RATE,128000);
            s.audio=new Enc(MediaCodec.createEncoderByType("audio/mp4a-latm"));
            s.audio.codec.configure(af,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
            s.muxer=new MediaMuxer(temp.getAbsolutePath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            s.video.codec.start();s.audio.codec.start();
            VideoRenderer renderer=new VideoRenderer(cues,beat);
            int audioFrames=audio.length/2,totalFrames=(int)Math.ceil(audioFrames/(double)PcmMix.RATE*FPS);
            int submitted=0;
            for(int f=0;f<totalFrames;f++){
                queueVideo(s,renderer,f,false);
                int until=Math.min(audioFrames,(int)Math.round((f+1)*PcmMix.RATE/(double)FPS));
                while(submitted<until){
                    int n=queueAudio(s,audio,submitted,until);
                    if(n<=0)throw new IllegalStateException("AAC input has zero capacity");
                    submitted+=n;
                }
                pump(s,s.video);pump(s,s.audio);
            }
            queueVideo(s,renderer,totalFrames,true);
            int ai=s.audio.codec.dequeueInputBuffer(100000);
            if(ai<0){pump(s,s.audio);ai=s.audio.codec.dequeueInputBuffer(100000);}
            if(ai<0)throw new IllegalStateException("AAC EOS input unavailable");
            s.audio.codec.queueInputBuffer(ai,0,0,audioFrames*1_000_000L/PcmMix.RATE,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            int polls=0;
            while(!(s.audio.ended&&s.video.ended)){
                pump(s,s.video);pump(s,s.audio);
                if(++polls>20000)throw new IllegalStateException("MP4 export encoder never reached EOS");
                if(polls%40==0)Thread.sleep(2);
            }
            if(!s.started)throw new IllegalStateException("Encoder never produced both MP4 tracks");
            s.muxer.stop();s.started=false;completed=true;
        } finally {
            if(s.video!=null){try{s.video.codec.stop();}catch(Exception ignored){}s.video.codec.release();}
            if(s.audio!=null){try{s.audio.codec.stop();}catch(Exception ignored){}s.audio.codec.release();}
            if(s.muxer!=null){if(s.started){try{s.muxer.stop();}catch(Exception ignored){}}s.muxer.release();}
            if(!completed)temp.delete();
        }
        try {
            ContentValues data=new ContentValues();
            data.put(MediaStore.MediaColumns.DISPLAY_NAME,filename);
            data.put(MediaStore.MediaColumns.MIME_TYPE,"video/mp4");
            data.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_MOVIES+"/SayBop");
            data.put(MediaStore.MediaColumns.IS_PENDING,1);
            Uri uri=context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,data);
            if(uri==null)throw new IOException("MediaStore could not create an output video");
            try {
                try(InputStream in=new FileInputStream(temp);
                    OutputStream out=context.getContentResolver().openOutputStream(uri)){
                    if(out==null)throw new IOException("Video destination unavailable");
                    byte[] block=new byte[65536];int n;
                    while((n=in.read(block))!=-1)out.write(block,0,n);
                }
                data.clear();data.put(MediaStore.MediaColumns.IS_PENDING,0);
                context.getContentResolver().update(uri,data,null,null);
                return uri;
            } catch(Exception ex){context.getContentResolver().delete(uri,null,null);throw ex;}
        } finally {temp.delete();}
    }
}