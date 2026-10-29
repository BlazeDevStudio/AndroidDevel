package com.saybop.app;
import android.graphics.*;
import java.util.*;

/** Draws offline, beat-keyed kinetic typography without GIF or external assets. */
public final class VideoRenderer {
    public static final int WIDTH=720, HEIGHT=1280;
    private final Bitmap bitmap=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888);
    private final Canvas canvas=new Canvas(bitmap);
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<WordTimeline.Cue> cues;
    private final BeatAnalyzer.Grid grid;
    private final int mode;
    private final AudioVisuals reactive;
    public VideoRenderer(List<WordTimeline.Cue> cues,BeatAnalyzer.Grid grid) {this(cues,grid,0);}
    public VideoRenderer(List<WordTimeline.Cue> cues,BeatAnalyzer.Grid grid,int mode) {
        this(cues,grid,mode,null);
    }
    public VideoRenderer(List<WordTimeline.Cue> cues,BeatAnalyzer.Grid grid,int mode,AudioVisuals reactive) {
        this.cues=cues;this.grid=grid;this.mode=Math.max(0,Math.min(mode,4));
        this.reactive=reactive;
    }
    public Bitmap frame(double seconds){
        canvas.drawColor(Color.rgb(6,10,20));
        double beat=grid.beatSeconds();
        double progress=((seconds-grid.phaseSeconds)/beat);
        double phase=progress-Math.floor(progress);
        double energy=reactive==null?0:reactive.loudness(seconds);
        double bass=reactive==null?0:reactive.bass(seconds);
        double onset=reactive==null?0:reactive.attack(seconds);
        double pulse=Math.min(1.3,Math.exp(-phase*8)*.65+onset*.55);
        p.setShader(new RadialGradient(WIDTH*.5f,(float)(HEIGHT*(.43+.04*Math.sin(seconds*.6))),
            (float)(WIDTH*(.5+pulse*.25+energy*.2)),new int[]{Color.rgb(22,62,124),Color.rgb(8,18,43),Color.rgb(5,9,19)},null,Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,WIDTH,HEIGHT,p);p.setShader(null);
        // Fine circuit-style blue grid and beat-reactive nodes
        p.setStrokeWidth(1);p.setStyle(Paint.Style.STROKE);
        p.setColor(Color.argb(46,50,132,210));
        for(int x=24;x<WIDTH;x+=54){canvas.drawLine(x,0,x,HEIGHT,p);}
        for(int y=20;y<HEIGHT;y+=54){canvas.drawLine(0,y,WIDTH,y,p);}
        p.setStyle(Paint.Style.FILL);
        for(int i=0;i<24;i++){
            double x=24+(i*111)%432;
            double y=20+(i*151)%810;
            p.setColor(Color.argb((int)(70+100*pulse),80,174,255));
            canvas.drawCircle((float)x,(float)y,(float)(1.8+2*pulse+2*bass),p);
        }
        // Music-reactive original artwork: symmetrical spectrum ribbons and bass orbit.
        // Every bar reads the same deterministic backing-track envelope used by MP4 export.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth((float)(2.0+4.0*bass));
        p.setColor(Color.argb((int)(90+120*Math.min(1,energy+onset*.3)),70,175,255));
        float ring=(float)(115+32*bass+24*pulse);
        canvas.drawCircle(WIDTH*.5f,HEIGHT*.46f,ring,p);
        if(mode==3) {
            p.setStrokeWidth((float)(2+6*onset));
            p.setColor(Color.argb((int)(80+150*energy),244,247,255));
            canvas.drawCircle(WIDTH*.5f,HEIGHT*.46f,ring*1.25f,p);
            canvas.drawCircle(WIDTH*.5f,HEIGHT*.46f,ring*1.52f,p);
        }
        p.setStyle(Paint.Style.FILL);
        final int bars=36;
        for(int i=0;i<bars;i++){
            double relative=(i-bars/2.0)*.0625;
            float local=reactive==null?0:reactive.loudness(seconds+relative);
            float localBass=reactive==null?0:reactive.bass(seconds+relative);
            float height=(float)(9+local*(mode==4?132:88)+localBass*42);
            float x=31+i*(WIDTH-62f)/bars;
            float mid=HEIGHT-184;
            p.setColor(Color.argb(105+(int)(120*local),45+(int)(90*localBass),150+(int)(85*local),255));
            canvas.drawRoundRect(x,mid-height*.5f,x+Math.max(3,(WIDTH-62f)/bars-6),
                mid+height*.5f,3,3,p);
        }
        // Simple progress/caption frame for rendering consistency
        p.setColor(Color.rgb(102,191,255));p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(23);
        canvas.drawText("SAYBOP",WIDTH/2f,85,p);
        p.setColor(Color.argb(160,255,255,255));p.setTextSize(16);
        canvas.drawText("TEXT  ·  BEAT  ·  VIDEO",WIDTH/2f,113,p);
        WordTimeline.Cue cue=WordTimeline.at(cues,seconds);
        if(cue!=null){
            double d=seconds-cue.startSeconds;
            double fadeIn=Math.min(1,d/.07);
            double fadeOut=Math.min(1,(cue.endSeconds-seconds)/.11);
            double pop=mode==1 ? 1.0 : 1+.23*Math.exp(-d*12)*Math.cos(d*24);
            if(mode==3)pop+=.08*onset;
            if(mode==4)pop+=.04*energy;
            if(mode==2)pop+=.1*Math.sin(2*Math.PI*(seconds-grid.phaseSeconds)/beat)*Math.exp(-d*3);
            String word=cue.text;
            p.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD));
            float font=84;
            p.setTextSize(font);
            while(font>26 && p.measureText(word)>WIDTH-54){font-=2;p.setTextSize(font);}
            p.setColor(Color.argb((int)(255*Math.max(0,Math.min(fadeIn,fadeOut))),247,251,255));
            p.setShadowLayer((float)(23+12*energy),0,0,Color.rgb(18,143,255));
            canvas.save();canvas.translate(WIDTH*.5f+
                (mode==1 ? (float)(1-fadeIn)*WIDTH*.5f : 0),HEIGHT*.48f+
                (mode==2 ? (float)(Math.sin(d*18)*32*Math.exp(-d*6)) : 0));
            canvas.scale((float)pop,(float)pop);
            canvas.drawText(word,0,0,p);canvas.restore();p.clearShadowLayer();
            p.setColor(Color.rgb(33,151,255));
            float w=Math.min(WIDTH-70,p.measureText(word));
            canvas.drawRoundRect((WIDTH-w)/2,HEIGHT*.52f,(WIDTH+w)/2,HEIGHT*.52f+5,2,2,p);
        }
        p.setColor(Color.argb(100,115,194,251));p.setStrokeWidth(3);
        canvas.drawLine(30,HEIGHT-62,WIDTH-30,HEIGHT-62,p);
        p.setColor(Color.rgb(67,187,255));
        int tick=(int)(WIDTH*.08+(WIDTH*.84)*phase);
        canvas.drawCircle(tick,HEIGHT-62,4+(float)pulse*6,p);
        return bitmap;
    }
}