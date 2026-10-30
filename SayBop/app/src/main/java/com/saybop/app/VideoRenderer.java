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
        this.cues=cues;this.grid=grid;this.mode=Math.max(0,Math.min(mode,5));
        this.reactive=reactive;
    }
    public Bitmap frame(double seconds){
        canvas.drawColor(Color.rgb(6,10,20));
        p.reset();p.setAntiAlias(true);
        int scene=mode==5?SceneDirector.scene(grid,seconds):Math.min(mode,4);
        int motion=SceneDirector.typographyMotion(mode,scene);
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
        drawScene(scene,seconds,energy,bass,onset,pulse);
        // Low-opacity circuit guides retained as parent brand geometry
        p.setStrokeWidth(1);p.setStyle(Paint.Style.STROKE);
        p.setColor(Color.argb(18,50,132,210));
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
        // Keep the generated video about the song, not a permanent software advertisement.
        p.setTextAlign(Paint.Align.CENTER);
        WordTimeline.Cue cue=WordTimeline.at(cues,seconds);
        if(cue!=null){
            double d=seconds-cue.startSeconds;
            double fadeIn=Math.min(1,d/.07);
            double fadeOut=Math.min(1,(cue.endSeconds-seconds)/.11);
            double pop=motion==1 ? 1.0 : 1+.23*Math.exp(-d*12)*Math.cos(d*24);
            if(mode==3)pop+=.08*onset;
            if(mode==4)pop+=.04*energy;
            if(motion==2)pop+=.1*Math.sin(2*Math.PI*(seconds-grid.phaseSeconds)/beat)*Math.exp(-d*3);
            String word=cue.text;
            p.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD));
            float font=84;
            p.setTextSize(font);
            while(font>26 && p.measureText(word)>WIDTH-54){font-=2;p.setTextSize(font);}
            p.setColor(Color.argb((int)(255*Math.max(0,Math.min(fadeIn,fadeOut))),247,251,255));
            p.setShadowLayer((float)(23+12*energy),0,0,Color.rgb(18,143,255));
            canvas.save();canvas.translate(WIDTH*.5f+
                (motion==1 ? (float)(1-fadeIn)*WIDTH*.5f : 0),HEIGHT*.48f+
                (motion==2 ? (float)(Math.sin(d*18)*32*Math.exp(-d*6)) : 0));
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
        if(mode==5){
            float flash=SceneDirector.downbeatFlash(grid,seconds);
            if(flash>0.001f){
                p.clearShadowLayer();
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.argb((int)(35*flash),244,247,255));
                canvas.drawRect(0,0,WIDTH,HEIGHT,p);
            }
        }
        return bitmap;
    }

    private void drawScene(int scene,double seconds,double energy,double bass,double onset,double pulse){
        // Distinct original Canvas arrangements; no proprietary Ditty sprites/shaders.
        float cx=WIDTH/2f,cy=HEIGHT*.46f;
        int blue=Color.rgb(38,142,250),cyan=Color.rgb(90,204,255);
        int white=Color.rgb(239,247,255),ruby=Color.rgb(241,8,67);
        float kick=(float)(onset*.7+pulse*.3);
        p.clearShadowLayer();p.setShader(null);p.setStyle(Paint.Style.STROKE);
        switch(scene){
            case 0: { // sweeping bands, like a moving audio oscilloscope
                p.setStrokeWidth(3+(float)bass*5);
                for(int line=0;line<4;line++){
                    p.setColor(Color.argb(105+line*28,70+line*18,149+line*17,255));
                    Path path=new Path();
                    float mid=cy-170+line*105;
                    for(int x=0;x<=WIDTH;x+=18){
                        float wave=(float)Math.sin(x*.018+seconds*(1.7+line*.13)+line*.8);
                        float y=mid+wave*(26+(float)energy*72)+(float)Math.sin(x*.006+seconds)*9;
                        if(x==0)path.moveTo(x,y);else path.lineTo(x,y);
                    }
                    canvas.drawPath(path,p);
                }
                break;
            }
            case 1: { // falling light trails driven by music transients
                for(int i=0;i<58;i++){
                    double drift=((seconds*(60+32*energy)+i*97)%1480+1480)%1480;
                    float y=(float)(drift-100);
                    float x=24+(i*173)%672;
                    p.setStrokeWidth((float)(1+kick*4));
                    p.setColor(Color.argb(60+(i%5)*20,100+(i%3)*35,164,255));
                    canvas.drawLine(x,y,x-12-(float)bass*15,y+42+(float)energy*86,p);
                }
                break;
            }
            case 2: { // radial rhythm fan
                for(int i=0;i<42;i++){
                    double angle=(Math.PI*2*i/42)+seconds*.1;
                    double inner=86+18*bass;
                    double outer=inner+58+energy*86+(i%3)*11+24*kick;
                    p.setColor(Color.argb(70+(i%5)*28,i%7==0?241:65,130+(i%4)*20,i%7==0?114:255));
                    p.setStrokeWidth(2+(i%3));
                    canvas.drawLine(cx+(float)(Math.cos(angle)*inner),cy+(float)(Math.sin(angle)*inner),
                        cx+(float)(Math.cos(angle)*outer),cy+(float)(Math.sin(angle)*outer),p);
                }
                break;
            }
            case 3: { // sliding light-panel composition
                p.setStyle(Paint.Style.FILL);
                for(int i=0;i<6;i++){
                    float shift=(float)Math.sin(seconds*.8+i*1.7)*28;
                    float x=40+i*120+shift;
                    float width=21+(float)(bass*30);
                    p.setColor(Color.argb(23+i*11,i%2==0?36:100,110+i*15,255));
                    canvas.drawRoundRect(x,170,x+width,HEIGHT-310,12,12,p);
                }
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(4+(float)onset*7);
                p.setColor(cyan);
                canvas.drawRoundRect(120,HEIGHT*.23f,WIDTH-120,HEIGHT*.73f,28,28,p);
                break;
            }
            case 4: { // off-axis prism diamonds
                p.setStrokeWidth(3+(float)bass*6);
                for(int i=0;i<7;i++){
                    float radius=65+i*36+(float)(kick*27);
                    Path diamond=new Path();
                    diamond.moveTo(cx,cy-radius);
                    diamond.lineTo(cx+radius*1.16f,cy);
                    diamond.lineTo(cx,cy+radius);
                    diamond.lineTo(cx-radius*1.16f,cy);
                    diamond.close();
                    p.setColor(Color.argb(65+i*19,70+i*21,132+i*13,255));
                    canvas.drawPath(diamond,p);
                }
                break;
            }
            default: { // concentric ripples and ruby beat accents
                for(int i=0;i<7;i++){
                    float radius=64+i*45+(float)(kick*35);
                    p.setColor(i%3==0?Color.argb(120,ruby>>16&255,ruby>>8&255,ruby&255):
                        Color.argb(65+i*17,95,180,255));
                    p.setStrokeWidth(2+(float)(energy*7));
                    canvas.drawCircle(cx,cy,radius,p);
                }
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.argb((int)(80+130*Math.min(1,onset)),241,8,67));
                canvas.drawCircle(cx,cy,14+(float)bass*19,p);
                break;
            }
        }
        p.setShader(null);p.setStyle(Paint.Style.FILL);p.clearShadowLayer();
    }
}