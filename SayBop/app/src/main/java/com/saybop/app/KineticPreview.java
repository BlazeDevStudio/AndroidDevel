package com.saybop.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import java.util.List;
import java.util.function.DoubleSupplier;

/** Displays the video renderer using the player-provided elapsed time. */
public final class KineticPreview extends View {
    private final DoubleSupplier playbackSeconds;
    private VideoRenderer renderer;

    public KineticPreview(Context context,DoubleSupplier playbackSeconds) {
        super(context);
        this.playbackSeconds=playbackSeconds;
    }
    public void setTimeline(List<WordTimeline.Cue> words,BeatAnalyzer.Grid grid,
                            int animationMode,AudioVisuals reactive) {
        renderer=new VideoRenderer(words,grid,animationMode,reactive);
        invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(renderer==null) {
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.rgb(24,65,120));
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(23*getResources().getDisplayMetrics().scaledDensity);
            canvas.drawColor(Color.rgb(5,13,30));
            canvas.drawText("YOUR WORDS WILL SING HERE",getWidth()/2f,getHeight()/2f,p);
            return;
        }
        Bitmap frame=renderer.frame(playbackSeconds.getAsDouble());
        canvas.drawBitmap(frame,null,new Rect(0,0,getWidth(),getHeight()),
            new Paint(Paint.FILTER_BITMAP_FLAG));
    }
}
