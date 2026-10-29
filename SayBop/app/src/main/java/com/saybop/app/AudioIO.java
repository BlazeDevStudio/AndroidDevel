package com.saybop.app;

import android.content.Context;
import android.media.*;
import android.net.Uri;
import java.io.*;
import java.nio.*;
import java.util.*;

public final class AudioIO {
    private AudioIO(){}
    public static final class Track {
        public final float[] stereo;
        public Track(float[] stereo){this.stereo=stereo;}
        public double duration(){return stereo.length/(2.0*PcmMix.RATE);}
    }
    private static final class Floats {
        float[] x=new float[65536];int n=0;
        void add(float v){if(n==x.length)x=Arrays.copyOf(x,x.length*2);x[n++]=v;}
        float[] done(){return Arrays.copyOf(x,n);}
    }
    public static Track decode(Context context, Uri uri, int limitSeconds) throws Exception {
        MediaExtractor extractor=new MediaExtractor();MediaCodec codec=null;
        try {
            extractor.setDataSource(context,uri,null);
            int selected=-1;MediaFormat fmt=null;
            for(int i=0;i<extractor.getTrackCount();i++) {
                MediaFormat test=extractor.getTrackFormat(i);
                String mime=test.getString(MediaFormat.KEY_MIME);
                if(mime!=null && mime.startsWith("audio/")) {selected=i;fmt=test;break;}
            }
            if(selected<0)throw new IOException("Selected file has no supported audio track");
            extractor.selectTrack(selected);
            String mime=fmt.getString(MediaFormat.KEY_MIME);
            codec=MediaCodec.createDecoderByType(mime);
            codec.configure(fmt,null,null,0);codec.start();
            int sampleRate=fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)?fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE):44100;
            int channels=fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)?fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT):2;
            int encoding=AudioFormat.ENCODING_PCM_16BIT;
            boolean inputDone=false,outputDone=false;
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            Floats decoded=new Floats();
            long maxSamples=(long)limitSeconds*96000*2;
            int idle=0;
            while(!outputDone && decoded.n<maxSamples) {
                if(!inputDone){
                    int idx=codec.dequeueInputBuffer(10000);
                    if(idx>=0){
                        ByteBuffer in=codec.getInputBuffer(idx);
                        if(in==null)throw new IOException("Decoder input buffer null");
                        in.clear();int size=extractor.readSampleData(in,0);
                        long time=extractor.getSampleTime();
                        if(size<0 || time>limitSeconds*1_000_000L){
                            codec.queueInputBuffer(idx,0,0,Math.max(0,time),MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true;
                        }else {codec.queueInputBuffer(idx,0,size,time,0);extractor.advance();}
                    }
                }
                int index=codec.dequeueOutputBuffer(info,10000);
                if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                    MediaFormat out=codec.getOutputFormat();
                    if(out.containsKey(MediaFormat.KEY_SAMPLE_RATE))sampleRate=out.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    if(out.containsKey(MediaFormat.KEY_CHANNEL_COUNT))channels=out.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if(out.containsKey(MediaFormat.KEY_PCM_ENCODING))encoding=out.getInteger(MediaFormat.KEY_PCM_ENCODING);
                    if(encoding!=AudioFormat.ENCODING_PCM_16BIT && encoding!=AudioFormat.ENCODING_PCM_FLOAT)
                        throw new IOException("Unsupported decoded PCM representation: "+encoding);
                }else if(index>=0){
                    ByteBuffer out=codec.getOutputBuffer(index);
                    if(out!=null && info.size>0){
                        out.position(info.offset);out.limit(info.offset+info.size);out.order(ByteOrder.LITTLE_ENDIAN);
                        int bytes=encoding==AudioFormat.ENCODING_PCM_FLOAT?4:2;
                        while(out.remaining()>=bytes*channels && decoded.n<maxSamples){
                            float left=0,right=0;
                            for(int c=0;c<channels;c++){
                                float v=encoding==AudioFormat.ENCODING_PCM_FLOAT?out.getFloat():out.getShort()/32768f;
                                if(c==0)left=v;else if(c==1)right=v;
                            }
                            if(channels==1)right=left;
                            decoded.add(left);decoded.add(right);
                        }
                    }
                    outputDone=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                    codec.releaseOutputBuffer(index,false);idle=0;
                }else if(++idle>500)throw new IOException("Audio decoding stalled");
            }
            float[] raw=decoded.done();
            if(raw.length==0)throw new IOException("No PCM samples decoded");
            int outFrames=Math.min(limitSeconds*PcmMix.RATE,
                    (int)(raw.length/2.0*PcmMix.RATE/sampleRate));
            float[] resampled=new float[outFrames*2];
            for(int i=0;i<outFrames;i++){
                double pos=i*sampleRate/(double)PcmMix.RATE;
                int a=Math.min(raw.length/2-1,(int)pos),b=Math.min(raw.length/2-1,a+1);
                float f=(float)(pos-a);
                for(int ch=0;ch<2;ch++)resampled[i*2+ch]=raw[a*2+ch]*(1-f)+raw[b*2+ch]*f;
            }
            return new Track(resampled);
        } finally {
            if(codec!=null){try{codec.stop();}catch(Exception ignored){}codec.release();}
            extractor.release();
        }
    }
    public static float[] wavMono(File file) throws IOException {
        try(RandomAccessFile r=new RandomAccessFile(file,"r")) {
            if(r.length()<44)throw new IOException("TTS WAV file too short");
            if(r.length()>10_000_000)throw new IOException("TTS WAV exceeds 10 MB safety limit");
            byte[] bytes=new byte[(int)r.length()];r.readFully(bytes);
            ByteBuffer b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            if(!fourcc(bytes,0,"RIFF")||!fourcc(bytes,8,"WAVE"))throw new IOException("TTS engine did not produce PCM WAV");
            int channels=0,rate=0,bits=0,format=0,offset=-1,len=0;
            int p=12;
            while(p+8<=bytes.length){
                int sz=b.getInt(p+4);if(sz<0||p+8L+sz>bytes.length)throw new IOException("TTS WAV has a truncated or invalid chunk");
                if(fourcc(bytes,p,"fmt ")){
                    if(sz>=16){format=b.getShort(p+8)&0xffff;channels=b.getShort(p+10)&0xffff;
                        rate=b.getInt(p+12);bits=b.getShort(p+22)&0xffff;}
                }else if(fourcc(bytes,p,"data")){offset=p+8;len=sz;break;}
                p+=8+sz+(sz&1);
            }
            if(format!=1 || bits!=16 || channels<1 || channels>2 || rate<8000 || rate>96000 || offset<0 || len<=0 || (long)offset+len>bytes.length || len%(2*channels)!=0)
                throw new IOException("Offline TTS voice output is not supported 16-bit WAV PCM");
            int frames=len/(2*channels);float[] mono=new float[frames];
            for(int i=0;i<frames;i++){
                int acc=0;for(int c=0;c<channels;c++)acc+=b.getShort(offset+(i*channels+c)*2);
                mono[i]=acc/(channels*32768f);
            }
            int count=Math.max(1,(int)(mono.length*(PcmMix.RATE/(double)rate)));
            float[] result=new float[count];
            for(int i=0;i<count;i++){
                double x=i*rate/(double)PcmMix.RATE;int a=Math.min(mono.length-1,(int)x),d=Math.min(mono.length-1,a+1);
                result[i]=(float)(mono[a]*(1-(x-a))+mono[d]*(x-a));
            }
            return result;
        }
    }
    private static boolean fourcc(byte[] bytes,int at,String code){
        if(at+4>bytes.length)return false;
        for(int i=0;i<4;i++)if((byte)code.charAt(i)!=bytes[at+i])return false;
        return true;
    }
    public static File writePreviewWav(Context ctx,float[] stereo) throws IOException {
        File file=new File(ctx.getCacheDir(),"saybop_preview.wav");
        try(FileOutputStream out=new FileOutputStream(file)){
            int data=stereo.length*2;
            ByteBuffer h=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
            h.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));h.putInt(36+data);
            h.put("WAVEfmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII));h.putInt(16);
            h.putShort((short)1);h.putShort((short)2);h.putInt(PcmMix.RATE);
            h.putInt(PcmMix.RATE*4);h.putShort((short)4);h.putShort((short)16);
            h.put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII));h.putInt(data);
            out.write(h.array());
            byte[] block=new byte[4096*2];int x=0;
            while(x<stereo.length){int count=Math.min(block.length/2,stereo.length-x);
                for(int i=0;i<count;i++){short v=PcmMix.pcm16(stereo[x+i]);block[i*2]=(byte)v;block[i*2+1]=(byte)(v>>8);}
                out.write(block,0,count*2);x+=count;
            }
        }
        return file;
    }
}