package com.saybop.app;

import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Encapsulates the platform speech engine's asynchronous file synthesis.
 * No network speech provider is selected here: callers supply installed offline Voices.
 * The Android engine remains owned/shut down by MainActivity.
 */
public final class SystemTtsSynth {
    private final TextToSpeech tts;
    private final ConcurrentHashMap<String,CountDownLatch> pending=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String,Boolean> finished=new ConcurrentHashMap<>();

    public SystemTtsSynth(TextToSpeech tts) {
        if(tts==null)throw new IllegalArgumentException("Missing Android TTS engine");
        this.tts=tts;
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) {}
            @Override public void onDone(String id) { complete(id,true); }
            @Override public void onError(String id) { complete(id,false); }
            @Override public void onError(String id,int code) { complete(id,false); }
        });
    }

    private void complete(String id, boolean success) {
        finished.put(id,success);
        CountDownLatch latch=pending.get(id);
        if(latch!=null)latch.countDown();
    }

    public float[] renderWord(String word, Voice voice, File cacheDir) throws Exception {
        if(word==null || word.isEmpty() || voice==null || cacheDir==null)
            throw new IllegalArgumentException("Missing word, offline voice or cache directory");
        File wav=new File(cacheDir,"voice_"+System.nanoTime()+".wav");
        String id="saybop_voice_"+System.nanoTime();
        CountDownLatch latch=new CountDownLatch(1);
        pending.put(id,latch);
        try {
            tts.setVoice(voice);
            tts.setPitch(1.0f);
            tts.setSpeechRate(1.06f);
            int result=tts.synthesizeToFile(word,new Bundle(),wav,id);
            if(result!=TextToSpeech.SUCCESS)
                throw new IOException("TTS engine rejected word: "+word);
            if(!latch.await(25,TimeUnit.SECONDS) || !Boolean.TRUE.equals(finished.remove(id)))
                throw new IOException("Offline TTS could not synthesize: "+word);
            return AudioIO.wavMono(wav);
        } finally {
            pending.remove(id);
            finished.remove(id);
            wav.delete();
        }
    }
}
