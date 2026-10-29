package dev.blaze.ditty;
import android.graphics.*;
import java.util.*;

/** Draws offline, beat-keyed kinetic typography without GIF or external assets. */
public final class VideoRenderer {
    public static final int WIDTH=480, HEIGHT=848;
    private final Bitmap bitmap=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888);
    private final Canvas canvas=new Canvas(bitmap);
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<WordTimeline.Cue> cues;
    private final BeatAnalyzer.Grid grid;
    public VideoRenderer(List<WordTimeline.Cue> cues,BeatAnalyzer.Grid grid) {this.cues=cues;this.grid=grid;}
    public Bitmap frame(double seconds){
        canvas.drawColor(Color.rgb(6,10,20));
        double beat=grid.beatSeconds();
        double progress=((seconds-grid.phaseSeconds)/beat);
        double phase=progress-Math.floor(progress);
        double pulse=Math.exp(-phase*8);
        p.setShader(new RadialGradient(WIDTH*.5f,(float)(HEIGHT*(.43+.04*Math.sin(seconds*.6))),
            (float)(WIDTH*(.5+pulse*.35)),new int[]{Color.rgb(22,62,124),Color.rgb(8,18,43),Color.rgb(5,9,19)},null,Shader.TileMode.CLAMP));
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
            canvas.drawCircle((float)x,(float)y,(float)(1.8+2*pulse),p);
        }
        // Simple progress/caption frame for rendering consistency
        p.setColor(Color.rgb(102,191,255));p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(23);
        canvas.drawText("BLAZE DITTY",WIDTH/2f,85,p);
        p.setColor(Color.argb(160,255,255,255));p.setTextSize(16);
        canvas.drawText("TEXT  ·  BEAT  ·  VIDEO",WIDTH/2f,113,p);
        WordTimeline.Cue cue=WordTimeline.at(cues,seconds);
        if(cue!=null){
            double d=seconds-cue.startSeconds;
            double fadeIn=Math.min(1,d/.07);
            double fadeOut=Math.min(1,(cue.endSeconds-seconds)/.11);
            double pop=1+.23*Math.exp(-d*12)*Math.cos(d*24);
            String word=cue.text;
            p.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD));
            float font=84;
            p.setTextSize(font);
            while(font>26 && p.measureText(word)>WIDTH-54){font-=2;p.setTextSize(font);}
            p.setColor(Color.argb((int)(255*Math.max(0,Math.min(fadeIn,fadeOut))),247,251,255));
            p.setShadowLayer(23,0,0,Color.rgb(18,143,255));
            canvas.save();canvas.translate(WIDTH*.5f,HEIGHT*.48f);
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