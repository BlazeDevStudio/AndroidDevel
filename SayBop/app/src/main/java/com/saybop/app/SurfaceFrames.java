package com.saybop.app;

import android.graphics.Bitmap;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.view.Surface;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** H.264 encoder input Surface fed through GLES2/EGL, avoiding fragile flexible-YUV image input. */
final class SurfaceFrames implements AutoCloseable {
    private static final int EGL_RECORDABLE_ANDROID=0x3142;
    private EGLDisplay display=EGL14.EGL_NO_DISPLAY;
    private EGLContext context=EGL14.EGL_NO_CONTEXT;
    private EGLSurface window=EGL14.EGL_NO_SURFACE;
    private final int width,height;
    private int program,texture,attribute,texAttribute,sampler;
    private final FloatBuffer vertices;
    SurfaceFrames(Surface input,int w,int h) {
        width=w;height=h;
        float[] quad={-1,-1,0,1, 1,-1,1,1, -1,1,0,0, 1,1,1,0};
        vertices=ByteBuffer.allocateDirect(quad.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertices.put(quad).position(0);
        try {
            display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if(display==EGL14.EGL_NO_DISPLAY || !EGL14.eglInitialize(display,new int[2],0,new int[2],0))
                throw new IllegalStateException("EGL display unavailable");
            int[] attrs={EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,
                EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_RENDERABLE_TYPE,4,
                EGL_RECORDABLE_ANDROID,1,EGL14.EGL_NONE};
            EGLConfig[] configs=new EGLConfig[1];int[] num=new int[1];
            if(!EGL14.eglChooseConfig(display,attrs,0,configs,0,1,num,0)||num[0]<1)
                throw new IllegalStateException("No recordable GLES2 EGLConfig");
            context=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,
                new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
            if(context==null || context==EGL14.EGL_NO_CONTEXT)throw new IllegalStateException("EGL context failed");
            window=EGL14.eglCreateWindowSurface(display,configs[0],input,
                new int[]{EGL14.EGL_NONE},0);
            if(window==null || window==EGL14.EGL_NO_SURFACE)throw new IllegalStateException("EGL window failed");
            if(!EGL14.eglMakeCurrent(display,window,window,context))throw new IllegalStateException("EGL bind failed");
            final String vertex="attribute vec2 aPos; attribute vec2 aUv; varying vec2 vUv; void main(){ gl_Position=vec4(aPos,0.,1.); vUv=aUv; }";
            final String fragment="precision mediump float; varying vec2 vUv; uniform sampler2D tex; void main(){gl_FragColor=texture2D(tex,vUv);}";
            int vs=shader(GLES20.GL_VERTEX_SHADER,vertex),fs=shader(GLES20.GL_FRAGMENT_SHADER,fragment);
            program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vs);GLES20.glAttachShader(program,fs);
            GLES20.glLinkProgram(program);
            int[] status=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,status,0);
            GLES20.glDeleteShader(vs);GLES20.glDeleteShader(fs);
            if(status[0]==0)throw new IllegalStateException("GLES program: "+GLES20.glGetProgramInfoLog(program));
            attribute=GLES20.glGetAttribLocation(program,"aPos");
            texAttribute=GLES20.glGetAttribLocation(program,"aUv");
            sampler=GLES20.glGetUniformLocation(program,"tex");
            int[] ids=new int[1];GLES20.glGenTextures(1,ids,0);texture=ids[0];
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        }catch(RuntimeException ex){close();throw ex;}
    }
    private static int shader(int kind,String source) {
        int sh=GLES20.glCreateShader(kind);
        GLES20.glShaderSource(sh,source);GLES20.glCompileShader(sh);
        int[] ok=new int[1];GLES20.glGetShaderiv(sh,GLES20.GL_COMPILE_STATUS,ok,0);
        if(ok[0]==0)throw new IllegalStateException("GLES shader: "+GLES20.glGetShaderInfoLog(sh));
        return sh;
    }
    void draw(Bitmap bitmap,long ptsUs) {
        if(bitmap.getWidth()!=width||bitmap.getHeight()!=height)throw new IllegalArgumentException("Frame size mismatch");
        GLES20.glViewport(0,0,width,height);
        GLES20.glUseProgram(program);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0);
        GLES20.glUniform1i(sampler,0);
        vertices.position(0);
        GLES20.glEnableVertexAttribArray(attribute);
        GLES20.glVertexAttribPointer(attribute,2,GLES20.GL_FLOAT,false,16,vertices);
        vertices.position(2);
        GLES20.glEnableVertexAttribArray(texAttribute);
        GLES20.glVertexAttribPointer(texAttribute,2,GLES20.GL_FLOAT,false,16,vertices);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
        int error=GLES20.glGetError();
        if(error!=GLES20.GL_NO_ERROR)throw new IllegalStateException("GLES frame error "+error);
        EGLExt.eglPresentationTimeANDROID(display,window,ptsUs*1000L);
        if(!EGL14.eglSwapBuffers(display,window))throw new IllegalStateException("Encoder EGL frame swap failed");
    }
    @Override public void close() {
        if(display==EGL14.EGL_NO_DISPLAY)return;
        EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
        if(window!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,window);
        if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,context);
        EGL14.eglTerminate(display);
        display=EGL14.EGL_NO_DISPLAY;window=EGL14.EGL_NO_SURFACE;context=EGL14.EGL_NO_CONTEXT;
    }
}
